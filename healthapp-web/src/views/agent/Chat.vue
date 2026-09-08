<template>
  <div class="agent-page">
    <div class="agent-container">
      <!-- 侧边栏：会话列表 -->
      <div class="sidebar">
        <div class="sidebar-header">
          <el-button type="primary" class="new-chat-btn" @click="newChat">
            <el-icon><Plus /></el-icon> 新建对话
          </el-button>
        </div>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: currentSession === s.id }"
            @click="switchSession(s.id)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span class="session-title">{{ s.title }}</span>
            <el-icon class="delete-btn" @click.stop="deleteSession(s.id)"><Delete /></el-icon>
          </div>
        </div>
      </div>

      <!-- 主聊天区 -->
      <div class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <el-avatar :size="40" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon :size="24"><Robot /></el-icon>
            </el-avatar>
            <div>
              <div class="agent-name">医智助手</div>
              <div class="agent-status"><span class="status-dot"></span>在线 · 医疗AI助手</div>
            </div>
          </div>
          <div class="header-right">
            <el-tag type="success" size="small">RAG检索</el-tag>
            <el-tag type="warning" size="small">工具调用</el-tag>
            <el-button text @click="clearChat"><el-icon><Delete /></el-icon> 清空</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="chat-messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="welcome">
            <el-avatar :size="64" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon :size="36"><Robot /></el-icon>
            </el-avatar>
            <h2>您好，我是医智助手</h2>
            <p>我可以帮您解答医学问题、查询预约信息、分析病历、推荐药品</p>
            <div class="quick-questions">
              <div class="quick-q" @click="sendQuick('高血压的诊断标准是什么？')">高血压的诊断标准是什么？</div>
              <div class="quick-q" @click="sendQuick('感冒发烧应该怎么处理？')">感冒发烧应该怎么处理？</div>
              <div class="quick-q" @click="sendQuick('查询我的预约记录')">查询我的预约记录</div>
              <div class="quick-q" @click="sendQuick('阿莫西林的用法用量和禁忌')">阿莫西林的用法用量和禁忌</div>
            </div>
          </div>

          <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="msg.role">
            <el-avatar v-if="msg.role === 'assistant'" :size="36" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon><Robot /></el-icon>
            </el-avatar>
            <el-avatar v-else :size="36" style="background: #409eff">
              {{ userStore.userInfo?.name?.charAt(0) }}
            </el-avatar>
            <div class="message-content">
              <div class="message-bubble" v-html="formatContent(msg.content)"></div>
              <div v-if="msg.role === 'assistant' && msg.tools" class="tool-tags">
                <el-tag v-for="t in msg.tools" :key="t" size="small" type="info">
                  <el-icon><Tools /></el-icon> {{ t }}
                </el-tag>
              </div>
              <div v-if="msg.role === 'assistant'" class="message-actions">
                <el-button text size="small" @click="copyMsg(msg.content)"><el-icon><CopyDocument /></el-icon> 复制</el-button>
                <el-button text size="small" @click="feedback(idx, 1)"><el-icon><CaretTop /></el-icon> 有用</el-button>
                <el-button text size="small" @click="feedback(idx, 0)"><el-icon><CaretBottom /></el-icon> 无用</el-button>
              </div>
            </div>
          </div>

          <div v-if="loading" class="message assistant">
            <el-avatar :size="36" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon><Robot /></el-icon>
            </el-avatar>
            <div class="message-content">
              <div class="typing">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <div class="input-wrapper">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              placeholder="请输入您的问题，我将为您提供专业的医疗建议..."
              @keydown.enter.exact.prevent="sendMessage"
              resize="none"
            />
            <el-button
              type="primary"
              class="send-btn"
              :loading="loading"
              :disabled="!inputText.trim()"
              @click="sendMessage"
            >
              <el-icon><Promotion /></el-icon> 发送
            </el-button>
          </div>
          <div class="input-tip">
            <el-icon><InfoFilled /></el-icon>
            AI回答仅供参考，不能替代专业医生诊断。急症请立即拨打120。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { Plus, Delete, ChatDotRound, Robot, Tools, CopyDocument, CaretTop, CaretBottom, Promotion, InfoFilled } from '@element-plus/icons-vue'

const userStore = useUserStore()
const messagesRef = ref<HTMLElement>()
const inputText = ref('')
const loading = ref(false)
const currentSession = ref('s1')

interface Message {
  role: 'user' | 'assistant'
  content: string
  tools?: string[]
}

const messages = ref<Message[]>([])
const sessions = ref([
  { id: 's1', title: '高血压咨询' },
  { id: 's2', title: '用药查询' },
])

const mockResponses: Record<string, string> = {
  '高血压的诊断标准是什么？': '根据《中国高血压防治指南》，高血压的诊断标准如下：\n\n**诊断标准：**\n在未使用降压药物的情况下，非同日3次测量诊室血压，收缩压≥140mmHg和/或舒张压≥90mmHg。\n\n**血压分级：**\n- 正常血压：<120/80mmHg\n- 正常高值：120-139/80-89mmHg\n- 1级高血压：140-159/90-99mmHg\n- 2级高血压：160-179/100-109mmHg\n- 3级高血压：≥180/110mmHg\n\n**注意事项：**\n1. 患者既往有高血压史，目前正在使用降压药物，血压虽然低于140/90mmHg，也应诊断为高血压\n2. 建议家庭自测血压作为补充\n3. 确诊后应在医生指导下规范治疗',
  '感冒发烧应该怎么处理？': '上呼吸道感染（感冒）的处理原则：\n\n**1. 对症治疗**\n- 休息、多饮水、清淡饮食\n- 体温<38.5℃：物理降温为主\n- 体温≥38.5℃：可使用对乙酰氨基酚或布洛芬\n\n**2. 常用药物**\n- 解热镇痛：对乙酰氨基酚、布洛芬\n- 鼻塞：伪麻黄碱\n- 抗过敏：氯雷他定\n\n**3. 重要提醒**\n- ⚠️ 抗生素仅在明确细菌感染时使用，不可滥用\n- 如持续高热超过3天、出现呼吸困难、剧烈咳嗽等症状，请及时就医\n- 儿童、孕妇、老年人用药需特别谨慎',
  '查询我的预约记录': '已为您查询到以下预约记录：\n\n**待就诊预约（2条）：**\n1. 2026-09-10 上午 - 内科 张明华主任医师 - 排队号3号\n2. 2026-09-12 下午 - 外科 李建国副主任医师 - 排队号1号\n\n**历史就诊（15条）：**\n最近一次：2026-08-20 内科 - 上呼吸道感染\n\n如需取消预约或查看详情，请前往"我的预约"页面。',
  '阿莫西林的用法用量和禁忌': '已为您查询阿莫西林的药品信息：\n\n**基本信息**\n- 通用名：阿莫西林\n- 规格：0.25g*24粒/盒\n- 价格：¥15.50\n- 分类：青霉素类抗生素\n\n**用法用量**\n- 成人：口服，一次0.5g，每6-8小时1次，一日剂量不超过4g\n- 小儿：每日剂量按体重20-40mg/kg，分3次服用\n\n**禁忌**\n- ⚠️ 青霉素过敏者禁用\n- 传染性单核细胞增多症患者禁用\n\n**不良反应**\n- 常见：恶心、呕吐、腹泻等胃肠道反应\n- 严重：过敏性休克（罕见但危险）\n\n**注意：使用前需确认无青霉素过敏史，必须在医生指导下使用！**',
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  // 模拟AI回复
  await new Promise(r => setTimeout(r, 1500))

  const response = mockResponses[text] || `感谢您的提问。关于"${text}"，我正在为您查询相关医学知识...\n\n根据检索到的资料，建议您：\n1. 注意观察症状变化\n2. 保持良好的生活习惯\n3. 如症状持续或加重，请及时就医\n\n⚠️ 以上内容仅供参考，具体诊疗请遵医嘱。`

  const tools = text.includes('预约') ? ['query_appointments'] :
                text.includes('阿莫西林') || text.includes('药品') ? ['query_drug_info'] :
                text.includes('高血压') || text.includes('诊断') ? ['medical_knowledge_search'] : []

  messages.value.push({ role: 'assistant', content: response, tools })
  loading.value = false
  scrollToBottom()
}

const sendQuick = (q: string) => {
  inputText.value = q
  sendMessage()
}

const formatContent = (text: string) => {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
    .replace(/⚠️/g, '⚠️')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const newChat = () => {
  const id = 's' + Date.now()
  sessions.value.unshift({ id, title: '新对话' })
  currentSession.value = id
  messages.value = []
}

const switchSession = (id: string) => {
  currentSession.value = id
  messages.value = []
}

const deleteSession = (id: string) => {
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (currentSession.value === id && sessions.value.length > 0) {
    currentSession.value = sessions.value[0].id
  }
}

const clearChat = () => { messages.value = [] }
const copyMsg = (text: string) => { navigator.clipboard.writeText(text); ElMessage.success('已复制') }
const feedback = (idx: number, type: number) => { ElMessage.success(type === 1 ? '感谢您的反馈' : '我们会持续改进') }

onMounted(() => {})
</script>

<style scoped lang="scss">
.agent-page { height: calc(100vh - 100px); }
.agent-container { display: flex; height: 100%; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.sidebar { width: 240px; background: #fafafa; border-right: 1px solid #ebeef5; display: flex; flex-direction: column; }
.sidebar-header { padding: 16px; }
.new-chat-btn { width: 100%; background: linear-gradient(135deg, #667eea, #764ba2); border: none; }
.session-list { flex: 1; overflow-y: auto; padding: 0 8px; }
.session-item {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 8px;
  cursor: pointer; font-size: 14px; color: #606266; margin-bottom: 4px;
  &:hover { background: #f0f2f5; }
  &.active { background: #ecf5ff; color: #409eff; }
  .session-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .delete-btn { opacity: 0; font-size: 14px; }
  &:hover .delete-btn { opacity: 1; }
}
.chat-main { flex: 1; display: flex; flex-direction: column; }
.chat-header {
  padding: 16px 24px; border-bottom: 1px solid #ebeef5;
  display: flex; justify-content: space-between; align-items: center;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.agent-name { font-size: 16px; font-weight: 600; }
.agent-status { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 6px; }
.status-dot { width: 8px; height: 8px; background: #67c23a; border-radius: 50%; display: inline-block; }
.header-right { display: flex; align-items: center; gap: 8px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 24px; }
.welcome { text-align: center; padding: 60px 20px; }
.welcome h2 { margin: 16px 0 8px; color: #303133; }
.welcome p { color: #909399; margin-bottom: 32px; }
.quick-questions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; max-width: 600px; margin: 0 auto; }
.quick-q {
  padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; text-align: left;
  cursor: pointer; font-size: 14px; color: #606266; transition: all 0.3s;
  &:hover { border-color: #409eff; color: #409eff; background: #ecf5ff; }
}
.message { display: flex; gap: 12px; margin-bottom: 24px; &.user { flex-direction: row-reverse; } }
.message-content { max-width: 70%; }
.message-bubble {
  padding: 12px 16px; border-radius: 12px; line-height: 1.6; font-size: 14px;
  .assistant & { background: #f5f7fa; color: #303133; border-top-left-radius: 4px; }
  .user & { background: linear-gradient(135deg, #667eea, #764ba2); color: white; border-top-right-radius: 4px; }
}
.tool-tags { margin-top: 8px; display: flex; gap: 6px; }
.message-actions { margin-top: 8px; display: flex; gap: 4px; opacity: 0; transition: opacity 0.3s; .message:hover & { opacity: 1; } }
.typing { display: flex; gap: 4px; padding: 12px 16px; background: #f5f7fa; border-radius: 12px; width: fit-content; }
.typing span { width: 8px; height: 8px; background: #c0c4cc; border-radius: 50%; animation: bounce 1.4s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-8px); } }
.chat-input { padding: 16px 24px; border-top: 1px solid #ebeef5; }
.input-wrapper { display: flex; gap: 12px; align-items: flex-end; }
.send-btn { height: 40px; padding: 0 20px; background: linear-gradient(135deg, #667eea, #764ba2); border: none; }
.input-tip { margin-top: 8px; font-size: 12px; color: #909399; display: flex; align-items: center; gap: 4px; }
</style>
