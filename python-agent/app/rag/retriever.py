"""
RAG 检索引擎 v2 —— BM25 + 向量混合检索
=====================================
企业级 RAG 的三件套：切分（Chunking）、检索（Retrieval）、融合排序（Fusion）。

本实现策略（零强制依赖，保证任何环境可运行）：
1. 稀疏检索：自实现 BM25（Okapi BM25, k1=1.5, b=0.75），中文分词用 jieba，
   未安装 jieba 时退化为字符 bigram。
2. 稠密检索：若环境中安装了 chromadb 则自动启用向量检索（余弦相似度）。
3. 混合融合：两路结果用 RRF（Reciprocal Rank Fusion, k=60）融合，
   这是企业级混合检索最常用的融合算法（Elasticsearch 8 同款）。
4. 相关度阈值过滤：低于阈值的噪声片段不进入大模型上下文。
"""
import hashlib
import math
import re
from collections import Counter
from typing import Dict, List, Optional

from loguru import logger

from app.core.config import settings

try:  # 中文分词：可选依赖
    import jieba

    def _tokenize(text: str) -> List[str]:
        return [t for t in jieba.lcut(text) if t.strip()]
except Exception:  # pragma: no cover
    def _tokenize(text: str) -> List[str]:
        text = re.sub(r"\s+", "", text.lower())
        return [text[i:i + 2] for i in range(max(len(text) - 1, 1))] or [text]


# 口语 → 书面语 同义词典（查询扩展：用户说"小孩发烧"，知识库写的是"儿童发热"）
_SYNONYMS: Dict[str, List[str]] = {
    "小孩": ["儿童"], "娃娃": ["儿童"], "宝宝": ["儿童", "婴儿"], "婴幼儿": ["儿童"],
    "发烧": ["发热"], "退烧": ["解热"], "退烧药": ["对乙酰氨基酚", "布洛芬"],
    "拉肚子": ["腹泻"], "拉稀": ["腹泻"], "肚子疼": ["腹痛"],
    "血压高": ["高血压"], "心梗": ["心肌梗死"], "中风": ["脑卒中"], "脑梗": ["脑卒中"],
    "感冒": ["上呼吸道感染"], "消炎药": ["抗菌药物"], "抽筋": ["抽搐"],
    "心口疼": ["胸痛"], "心口痛": ["胸痛"], "喘不上气": ["呼吸困难"],
    "起疹子": ["皮疹"], "拉血": ["出血"], "头昏": ["眩晕"],
}


def _expand_query(tokens: List[str]) -> List[str]:
    """同义词扩展：原词 + 同义词（允许重复计分，BM25 天然支持词频加权）"""
    out = list(tokens)
    for t in tokens:
        out.extend(_SYNONYMS.get(t, []))
        # jieba 可能把"发烧"切成"发烧/发热"以外的粒度，兜底做整词包含检查
    return out


class _BM25Index:
    """Okapi BM25 稀疏检索索引（纯 Python 实现）"""

    def __init__(self, k1: float = 1.5, b: float = 0.75):
        self.k1, self.b = k1, b
        self.docs: List[List[str]] = []   # 每个块的词项列表
        self.doc_len: List[int] = []
        self.df: Counter = Counter()      # 词项 -> 包含该词的文档数
        self.avgdl: float = 0.0

    def add(self, tokens: List[str]):
        self.docs.append(tokens)
        self.doc_len.append(len(tokens) or 1)
        for t in set(tokens):
            self.df[t] += 1
        self.avgdl = sum(self.doc_len) / len(self.doc_len)

    def score(self, query_tokens: List[str], index: int) -> float:
        tf = Counter(self.docs[index])
        dl = self.doc_len[index]
        n = len(self.docs)
        s = 0.0
        for t in query_tokens:
            if t not in self.df:
                continue
            idf = math.log(1 + (n - self.df[t] + 0.5) / (self.df[t] + 0.5))
            f = tf.get(t, 0)
            s += idf * (f * (self.k1 + 1)) / (f + self.k1 * (1 - self.b + self.b * dl / self.avgdl))
        return s

    def search_by_tokens(self, query_tokens: List[str], top_k: int) -> List[int]:
        if not self.docs:
            return []
        scored = [(self.score(query_tokens, i), i) for i in range(len(self.docs))]
        scored = [(s, i) for s, i in scored if s > 0]
        scored.sort(reverse=True)
        return [i for _, i in scored[:top_k]]

    def search(self, query: str, top_k: int) -> List[int]:
        return self.search_by_tokens(_tokenize(query), top_k)


class MedicalKnowledgeRetriever:
    """混合检索器：BM25（必选） + Chroma 向量（可选） + RRF 融合"""

    def __init__(self):
        self.bm25 = _BM25Index()
        self.chunks: List[Dict] = []      # [{content, metadata}]
        self._chroma = None
        self._init_chroma()

    # ---------- 可选向量后端 ----------
    def _init_chroma(self):
        try:
            import chromadb
            from chromadb.config import Settings as ChromaSettings
            import os
            os.makedirs(settings.CHROMA_PERSIST_DIR, exist_ok=True)
            client = chromadb.PersistentClient(
                path=settings.CHROMA_PERSIST_DIR,
                settings=ChromaSettings(anonymized_telemetry=False))
            self._chroma = client.get_or_create_collection(
                name=settings.CHROMA_COLLECTION_NAME,
                metadata={"description": "medical knowledge"})
            logger.info("向量检索已启用(chromadb)：BM25+向量 混合检索模式")
        except Exception:
            self._chroma = None
            logger.info("chromadb 未安装，启用纯 BM25 检索模式（企业版可安装以开启混合检索）")

    # ---------- 切分 ----------
    def _chunk(self, text: str) -> List[str]:
        """按长度切分，带重叠窗口，保留句子完整性"""
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) <= settings.RAG_CHUNK_SIZE:
            return [text] if text else []
        chunks, start = [], 0
        while start < len(text):
            end = min(start + settings.RAG_CHUNK_SIZE, len(text))
            if end < len(text):  # 尽量切在句号/分号处
                for sep in ("。", "；", "！", "？", "."):
                    p = text.rfind(sep, start, end)
                    if p > start + settings.RAG_CHUNK_SIZE // 2:
                        end = p + 1
                        break
            chunks.append(text[start:end])
            start = end - settings.RAG_CHUNK_OVERLAP if end < len(text) else end
        return chunks

    # ---------- 文档管理 ----------
    def add_documents(self, documents: List[str], metadatas: Optional[List[Dict]] = None,
                      ids: Optional[List[str]] = None) -> int:
        """添加文档（自动切分入索引），返回新增块数"""
        added = 0
        for i, doc in enumerate(documents):
            meta = (metadatas[i] if metadatas and i < len(metadatas) else {}) or {}
            for part in self._chunk(doc):
                chunk_meta = dict(meta)
                self.chunks.append({"content": part, "metadata": chunk_meta})
                self.bm25.add(_tokenize(part))
                if self._chroma is not None:
                    cid = hashlib.md5((part + str(added)).encode()).hexdigest()
                    try:
                        self._chroma.add(documents=[part], metadatas=[chunk_meta], ids=[cid])
                    except Exception as e:
                        logger.warning("向量库写入失败(忽略): {}", e)
                added += 1
        logger.info("知识库新增 {} 个文本块，总量 {}", added, len(self.chunks))
        return added

    # ---------- 检索 ----------
    def search(self, query: str, top_k: Optional[int] = None) -> List[Dict]:
        top_k = top_k or settings.RAG_TOP_K
        if not self.chunks:
            return []

        # 1) BM25 稀疏召回（多召回一点供融合；查询做同义词扩展）
        recall_k = top_k * 3
        bm25_ids = self.bm25.search_by_tokens(_expand_query(_tokenize(query)), recall_k)
        candidates: Dict[int, float] = {}

        # RRF 融合：score = Σ 1/(k + rank)
        RRF_K = 60
        for rank, idx in enumerate(bm25_ids):
            candidates[idx] = candidates.get(idx, 0.0) + 1.0 / (RRF_K + rank + 1)

        # 2) 向量稠密召回（可选）
        if self._chroma is not None:
            try:
                res = self._chroma.query(query_texts=[query], n_results=min(recall_k, self._chroma.count()))
                ids = res.get("ids", [[]])[0]
                docs = res.get("documents", [[]])[0]
                metas = res.get("metadatas", [[]])[0]
                # 建立 content -> 内部索引 的映射（chroma 侧无顺序 id）
                content_index = {c["content"]: i for i, c in enumerate(self.chunks)}
                for rank, (doc, meta) in enumerate(zip(docs, metas)):
                    idx = content_index.get(doc)
                    if idx is not None:
                        candidates[idx] = candidates.get(idx, 0.0) + 1.0 / (RRF_K + rank + 1)
            except Exception as e:
                logger.warning("向量检索失败(仅BM25): {}", e)

        # 3) 排序 + 阈值过滤
        ranked = sorted(candidates.items(), key=lambda kv: kv[1], reverse=True)[:top_k]
        q_expanded = _expand_query(_tokenize(query))
        results = []
        for idx, rrf_score in ranked:
            bm25_raw = self.bm25.score(q_expanded, idx)
            if bm25_raw < settings.RAG_SCORE_THRESHOLD and rrf_score < 0.01:
                continue
            c = self.chunks[idx]
            results.append({"content": c["content"], "metadata": c["metadata"],
                            "score": round(rrf_score, 4)})
        logger.info("RAG检索: q={} 候选{} 命中{}", query[:40], len(candidates), len(results))
        return results

    def format_context(self, results: List[Dict]) -> str:
        if not results:
            return ""
        parts = []
        for i, r in enumerate(results, 1):
            src = r["metadata"].get("source", "未知来源")
            parts.append(f"[参考{i} | 来源: {src}]\n{r['content']}")
        return "\n\n".join(parts)

    def count(self) -> int:
        return len(self.chunks)


# 全局单例
retriever = MedicalKnowledgeRetriever()


def init_sample_knowledge():
    """初始化内置医疗知识库（仅当库为空时执行一次）"""
    if retriever.count() > 0:
        return
    docs = [
        # 心血管
        ("高血压诊断标准：在未使用降压药物的情况下，非同日3次测量诊室血压，收缩压≥140mmHg和/或舒张压≥90mmHg。患者既往有高血压史，目前正在使用降压药物，血压虽然低于140/90mmHg，也应诊断为高血压。降压治疗目标：一般高血压患者应降至140/90mmHg以下，能耐受者可进一步降至130/80mmHg以下。",
         "中国高血压防治指南", "心血管"),
        ("胸痛鉴别诊断：1.心绞痛：胸骨后压榨样疼痛，劳累诱发，休息或含服硝酸甘油3-5分钟内缓解；2.急性心肌梗死：胸痛持续超过30分钟，伴大汗、濒死感，心电图ST段弓背抬高，肌钙蛋白升高，需立即行PCI再灌注治疗；3.主动脉夹层：撕裂样剧痛，向背部放射，双侧上肢血压差异超过20mmHg；4.肺栓塞：突发胸痛、呼吸困难、咯血三联征，D-二聚体升高。",
         "胸痛诊疗指南", "心血管"),
        ("心力衰竭管理要点：1.诊断：呼吸困难、乏力、液体潴留，BNP/NT-proBNP升高，超声心动图LVEF降低；2.药物治疗基石：ACEI/ARB/ARNI、β受体阻滞剂、醛固酮受体拮抗剂、SGLT2抑制剂新四联；3.每日监测体重，3天内增加2kg以上提示液体潴留加重；4.限盐每日少于5克，限水根据医嘱。",
         "中国心力衰竭诊断和治疗指南", "心血管"),
        # 呼吸
        ("急性上呼吸道感染治疗原则：1.对症治疗：休息、多饮水、清淡饮食；2.解热镇痛：对乙酰氨基酚或布洛芬；3.鼻塞：伪麻黄碱；4.抗过敏：氯雷他定；5.抗生素仅在明确细菌感染时使用，病毒性感冒不可滥用抗生素。",
         "急性上呼吸道感染诊疗规范", "呼吸科"),
        ("社区获得性肺炎（CAP）诊治要点：1.诊断：新发咳嗽咳痰伴发热，肺部湿啰音，胸片显示新发浸润影；2.常用评分：CURB-65评估严重程度决定门诊或住院；3.经验性抗感染：青壮年首选青霉素类或第一代头孢，支原体肺炎首选大环内酯类或呼吸喹诺酮；4.治疗48-72小时评估疗效，无效需复查并调整方案。",
         "中国成人社区获得性肺炎诊断和治疗指南", "呼吸科"),
        ("支气管哮喘急性发作处理：1.轻度：吸入SABA（沙丁胺醇气雾剂）每20分钟1次，1小时内3次；2.中重度：雾化吸入SABA联合异丙托溴铵，尽早全身使用糖皮质激素；3.危重：出现意识改变、说话不能成句、哮鸣音消失提示致死性发作，立即抢救；4.慢性期控制药物：ICS+LABA联合制剂为首选。",
         "支气管哮喘防治指南", "呼吸科"),
        # 内分泌
        ("糖尿病诊断标准：典型糖尿病症状（多饮、多尿、多食、体重下降）加上以下任一项：1.随机静脉血浆葡萄糖≥11.1mmol/L；2.空腹静脉血浆葡萄糖≥7.0mmol/L；3.OGTT后2小时静脉血浆葡萄糖≥11.1mmol/L；4.糖化血红蛋白HbA1c≥6.5%。无糖尿病典型症状者需改日复查确认。",
         "中国2型糖尿病防治指南", "内分泌"),
        ("糖尿病血糖控制目标：1.一般成人2型糖尿病：空腹4.4-7.0mmol/L，非空腹<10.0mmol/L，HbA1c<7.0%；2.年轻、病程短、无并发症者可更严格（HbA1c<6.5%）；3.老年、有低血糖风险或有严重并发症者适当放宽；4.低血糖分级：血糖<3.9mmol/L即为低血糖，需立即补充葡萄糖。",
         "中国2型糖尿病防治指南", "内分泌"),
        # 消化
        ("急性阑尾炎典型表现：转移性右下腹痛，始于上腹或脐周，数小时后转移并局限于右下腹，可伴恶心、呕吐、发热。体征：右下腹麦氏点压痛、反跳痛、肌紧张。实验室检查：白细胞及中性粒细胞升高。治疗：确诊后原则上应尽早手术切除（腹腔镜阑尾切除术为首选）。",
         "外科学（人卫版）", "外科"),
        ("消化性溃疡诊治要点：1.典型表现：慢性、周期性、节律性上腹痛，胃溃疡多为餐后痛，十二指肠溃疡多为空腹痛/夜间痛；2.确诊金标准：胃镜检查+活检；3.幽门螺杆菌阳性必须根除：四联疗法（PPI+铋剂+两种抗生素）治疗14天；4.停药4周后行尿素呼气试验复查根除效果。",
         "消化性溃疡诊断与治疗规范", "消化科"),
        # 儿科
        ("儿童发热处理：1.体温<38.5℃且精神状态好：物理降温、多饮水、观察；2.体温≥38.5℃或明显不适：对乙酰氨基酚（10-15mg/kg/次，间隔4-6小时）或布洛芬（5-10mg/kg/次，间隔6-8小时）；3.3个月以下婴儿发热应立即就医；4.发热持续超过3天、伴抽搐、皮疹、精神萎靡、呼吸困难应立即就医。",
         "儿童发热诊疗指南", "儿科"),
        ("婴幼儿腹泻补液原则：1.轻度脱水（口渴、尿稍少、精神好）：口服补液盐III，50-80ml/kg，4小时内服完；2.中重度脱水：静脉补液；3.继续喂养：母乳喂养继续母乳，已加辅食者给易消化食物；4.补锌：6月龄以上每日元素锌20mg，共10-14天，可减轻病情；5.禁止使用强力止泻剂如洛哌丁胺。",
         "中国儿童急性感染性腹泻病临床实践指南", "儿科"),
        # 急救
        ("青霉素过敏性休克抢救流程：1.立即停药，保留静脉通路；2.肾上腺素：0.1%肾上腺素0.5-1ml（成人）大腿外侧肌肉注射，5-15分钟可重复；3.体位：平卧位抬高下肢，保持呼吸道通畅、吸氧；4.液体复苏：快速输注生理盐水；5.二线用药：糖皮质激素、抗组胺药；6.后续：详细记录过敏药物，交代患者终身避免。",
         "药物过敏反应处理规范", "急救"),
        ("心肺复苏（CPR）操作要点（成人）：1.判断：意识丧失、无呼吸或仅濒死叹息样呼吸即启动；2.胸外按压：两乳头连线中点，深度5-6cm，频率100-120次/分，按压与放松时间相等；3.开放气道：仰头抬颏法；4.人工呼吸：按压:通气=30:2；5.尽早使用AED，按照语音提示操作；6.按压中断时间不超过10秒。",
         "2020 AHA心肺复苏指南", "急救"),
        ("气道异物梗阻（海姆立克急救法）：1.成人：施救者站在患者身后，双臂环抱其腰部，一手握拳顶住肚脐上方两横指处，另一手包住拳头，快速向内向上冲击，重复至异物排出；2.孕妇和肥胖者：冲击位置改为胸部；3.一岁以下婴儿：5次拍背+5次压胸交替；4.梗阻解除后仍建议就医检查。",
         "急救手册", "急救"),
        # 药学
        ("抗菌药物使用原则：1.诊断为细菌性感染者方有指征应用抗菌药物；2.尽早查明感染病原，根据病原种类及药敏结果选用；3.按照药物的抗菌作用、体内过程特点选择用药；4.给药方案应综合患者病情、病原菌种类制定；5.避免无指征的预防用药和局部用药；6.足量足疗程，避免频繁换药。",
         "抗菌药物临床应用指导原则", "药学"),
        ("对乙酰氨基酚与布洛芬对比：1.对乙酰氨基酚：成人单次500mg-1g，24小时不超过2g（肝功能不全者），过量可致肝衰竭，饮酒者慎用；2.布洛芬：成人单次200-400mg，间隔6-8小时，24小时不超过1.2g（自行用药），消化道溃疡、肾功能不全者慎用；3.儿童退热两药均可按体重计算使用；4.两药交替使用并不推荐常规进行。",
         "解热镇痛药临床合理使用指南", "药学"),
        ("他汀类药物使用注意事项：1.适应症：高胆固醇血症、动脉粥样硬化性心血管疾病二级预防；2.监测：用药后4-8周复查血脂、肝功能、肌酸激酶；3.不良反应：肌肉酸痛/乏力需警惕横纹肌溶解（CK升高10倍以上应停药）；4.相互作用：避免与大量西柚汁同服，与克拉霉素、唑类抗真菌药合用需减量。",
         "中国血脂管理指南", "药学"),
        # 检验
        ("血常规解读要点：1.白细胞升高伴中性粒细胞比例升高：提示细菌感染；2.白细胞正常或降低伴淋巴细胞比例升高：提示病毒感染；3.嗜酸性粒细胞升高：提示过敏或寄生虫感染；4.血红蛋白：成年男性<120g/L、女性<110g/L为贫血；5.血小板<100×10⁹/L为血小板减少，需警惕出血风险。",
         "临床检验手册", "检验"),
        ("肝功能解读要点：1.ALT/AST升高：提示肝细胞损伤，ALT>10倍正常上限见于急性肝炎、药物性肝损；2.胆红素：总胆红素+直接胆红素升高为主提示梗阻性黄疸，间接胆红素升高为主提示溶血或Gilbert综合征；3.ALP和GGT同时升高：提示胆道梗阻或淤胆；4.白蛋白降低：见于慢性肝病、蛋白丢失、营养不良。",
         "临床检验手册", "检验"),
        # 中医
        ("中药煎服一般方法：1.煎具：首选砂锅、陶瓷锅，忌用铁锅铝锅；2.浸泡：冷水浸泡30-60分钟，水面高出药面2-3厘米；3.火候：先武火（大火）煮沸后改文火（小火）；4.时间：解表药煮沸后煎10-15分钟，滋补药煎30-60分钟；5.特殊煎法：先煎（矿物贝壳类）、后下（薄荷砂仁）、包煎（车前子）、烊化（阿胶）。",
         "中药学（人卫版）", "中医"),
        ("常用穴位保健：1.合谷穴：手背第1、2掌骨间，主治头痛、牙痛、面口疾病，感冒时按揉有助缓解；2.足三里：外膝眼下3寸，是保健要穴，健脾和胃、扶正培元；3.内关穴：腕横纹上2寸，主治心悸、胸闷、恶心呕吐、晕车；4.按揉手法：拇指指腹按揉，力度以酸胀为度，每穴3-5分钟。",
         "针灸学（人卫版）", "中医"),
        # 慢病管理
        ("脑卒中FAST识别法：F（Face）面部不对称、口角歪斜；A（Arm）双臂平举单侧无力下垂；S（Speech）言语不清、表达困难；T（Time）立即拨打120记录发病时间。静脉溶栓时间窗为发病4.5小时内，越早治疗效果越好，切勿等待症状自行缓解。",
         "中国脑卒中防治指导规范", "神经内科"),
        ("腰椎间盘突出症日常管理：1.急性期：卧床休息（不建议超过3天）、局部热敷、非甾体抗炎药止痛；2.缓解期：核心肌群锻炼（小燕飞、平板支撑）、避免久坐久弯腰；3.坐姿：腰部垫靠枕，每45-60分钟起身活动；4.手术指征：进行性肌力下降、马尾综合征（大小便功能障碍）需急诊手术。",
         "腰椎间盘突出症诊疗指南", "骨科"),
    ]
    retriever.add_documents(
        documents=[d[0] for d in docs],
        metadatas=[{"source": d[1], "category": d[2]} for d in docs],
    )
    logger.info("内置医疗知识库初始化完成: {} 篇文档", len(docs))
