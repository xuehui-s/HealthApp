# 八股面试题大全 - Java基础与JVM篇

> 覆盖：Java基础、集合、JVM、垃圾回收、类加载

---

## 一、Java基础

### 1. Java的基本数据类型有哪些？各自占多少字节？

**答案**：
- byte：1字节，范围-128~127
- short：2字节
- int：4字节
- long：8字节
- float：4字节
- double：8字节
- char：2字节（Unicode）
- boolean：1字节（实际由JVM决定）

**注意**：基本类型不是对象，存在栈上；包装类型是对象，存在堆上。自动装箱拆箱是语法糖，编译时调用valueOf()和xxxValue()。

---

### 2. == 和 equals 的区别？

**答案**：
- `==`：比较基本类型时值比较，比较引用类型时比较内存地址
- `equals`：Object类默认实现是==比较，但String、Integer等重写后比较内容

**经典陷阱**：`Integer a = 127; Integer b = 127; a == b` 为true（Integer缓存池-128~127），但`Integer a = 128; Integer b = 128; a == b`为false。

---

### 3. String、StringBuilder、StringBuffer的区别？

**答案**：
- String：不可变，final修饰，每次修改创建新对象，适合常量
- StringBuilder：可变，非线程安全，性能高，适合单线程
- StringBuffer：可变，线程安全（synchronized），性能略低，适合多线程

**性能排序**：StringBuilder > StringBuffer > String（频繁修改时）

---

### 4. String为什么不可变？有什么好处？

**答案**：
String类被final修饰，内部char[]也被final修饰，且没有提供修改char[]的public方法。

**好处**：
1. 线程安全，多线程下不需要同步
2. 可以缓存hashCode，作为HashMap的key性能好
3. 字符串常量池可以复用，节省内存
4. 安全，作为参数传递不会被修改（如网络连接、文件路径）

---

### 5. 重载和重写的区别？

**答案**：
- 重载（Overload）：同一个类中，方法名相同，参数列表不同（个数/类型/顺序），与返回值无关。编译期多态。
- 重写（Override）：子类继承父类，方法名和参数列表相同，实现体不同。运行期多态。

**重写的限制**：
- 方法名、参数列表必须相同
- 子类方法访问权限不能比父类更严格
- 子类方法不能抛出比父类更宽泛的异常
- private/static/final方法不能被重写

---

### 6. 接口和抽象类的区别？

**答案**：

| 特性 | 接口 | 抽象类 |
|------|------|--------|
| 关键字 | interface | abstract class |
| 继承 | 多实现（implements多个） | 单继承（extends一个） |
| 构造方法 | 没有 | 有 |
| 成员变量 | 默认public static final | 任意 |
| 方法默认实现 | Java8后可有default/static方法 | 可有普通方法 |
| 设计目的 | 定义行为规范（can-do） | 代码复用（is-a） |

---

### 7. final、finally、finalize的区别？

**答案**：
- final：修饰类（不能被继承）、方法（不能被重写）、变量（不能被重新赋值）
- finally：try-catch-finally中的代码块，无论是否异常都会执行（除非JVM退出）
- finalize：Object类的方法，垃圾回收前调用，已废弃（Java9标记为deprecated）

---

### 8. Java的值传递和引用传递？

**答案**：Java只有值传递！
- 基本类型：传递值的副本
- 引用类型：传递引用地址的副本（不是对象本身）

**理解**：方法内修改引用指向的对象内容会影响原对象，但重新赋值引用不会影响原引用。

---

### 9. HashMap的底层原理？

**答案**：
JDK1.8后：数组 + 链表 + 红黑树

1. 默认初始容量16，负载因子0.75
2. put时计算key的hash值（hash = (h = key.hashCode()) ^ (h >>> 16)）
3. 计算数组下标：(n-1) & hash
4. 若该位置无元素直接放入
5. 若有元素，equals比较key，相同则覆盖value，不同则挂到链表
6. 链表长度>8且数组长度>64时，链表转红黑树
7. 元素个数超过容量*负载因子时，扩容为2倍并重新哈希

**为什么容量是2的幂**：保证(n-1)&hash等价于取模运算，且分布均匀。

---

### 10. HashMap和ConcurrentHashMap的区别？

**答案**：
- HashMap：非线程安全，允许null键值
- ConcurrentHashMap：线程安全，不允许null键值

**ConcurrentHashMap演进**：
- JDK1.7：Segment分段锁，每段一个ReentrantLock
- JDK1.8：CAS + synchronized，锁桶头节点，性能更高

**JDK1.8 ConcurrentHashMap put流程**：
1. 计算hash定位桶
2. 桶为空：CAS插入
3. 桶不为空：synchronized锁桶头节点
4. 链表/红黑树遍历，相同key覆盖，否则追加
5. 检查是否需要转红黑树和扩容

---

### 11. ArrayList和LinkedList的区别？

**答案**：

| 特性 | ArrayList | LinkedList |
|------|-----------|------------|
| 底层 | 动态数组 | 双向链表 |
| 随机访问 | O(1) | O(n) |
| 插入删除(尾部) | O(1) | O(1) |
| 插入删除(中间) | O(n)（需移动元素） | O(1)（找到位置后） |
| 内存占用 | 少（连续空间） | 大（需存前后指针） |

**使用场景**：查询多用ArrayList，频繁增删用LinkedList（实际开发中ArrayList用得更多）。

---

### 12. ArrayList的扩容机制？

**答案**：
- 默认初始容量10（懒加载，第一次add时才初始化）
- 扩容时新容量 = 旧容量 * 1.5
- 扩容通过Arrays.copyOf()复制到新数组
- 可通过ensureCapacity()预分配，减少扩容次数

---

### 13. 泛型的实现原理？类型擦除？

**答案**：Java泛型是伪泛型，编译时通过类型擦除实现。

**类型擦除规则**：
1. 无限制类型参数擦除为Object
2. 有限制类型参数（如<T extends Number>）擦除为限制类型
3. 插入必要的类型转换（checkcast）
4. 生成桥接方法（bridge method）保证多态

**验证**：`List<String>`和`List<Integer>`运行时是同一个Class对象。

---

## 二、JVM

### 14. JVM内存区域划分？

**答案**：

| 区域 | 线程 | 存储内容 | 异常 |
|------|------|----------|------|
| 程序计数器 | 私有 | 当前线程执行的字节码行号 | 无 |
| 虚拟机栈 | 私有 | 栈帧（局部变量表、操作数栈、方法出口） | StackOverflowError/OOM |
| 本地方法栈 | 私有 | Native方法 | 同上 |
| 堆 | 共享 | 对象实例、数组 | OOM |
| 方法区(元空间) | 共享 | 类信息、常量、静态变量、JIT编译代码 | OOM |

**JDK1.8变化**：方法区从永久代(PermGen)改为元空间(Metaspace)，使用本地内存，默认无上限。

---

### 15. 什么是垃圾？如何判断对象可回收？

**答案**：

**引用计数法**：对象被引用时+1，引用失效时-1，为0时可回收。缺点：无法解决循环引用。

**可达性分析算法**（JVM采用）：
从GC Roots出发，向下搜索，不可达的对象可回收。

**GC Roots包括**：
1. 虚拟机栈中引用的对象
2. 方法区中静态变量引用的对象
3. 方法区中常量引用的对象
4. 本地方法栈中JNI引用的对象
5. 活跃线程的引用

---

### 16. 垃圾回收算法有哪些？

**答案**：

1. **标记-清除**：标记可回收对象，然后清除。缺点：产生内存碎片。
2. **标记-复制**：将内存分两块，存活对象复制到另一块，清空当前块。缺点：内存利用率50%。
3. **标记-整理**：标记后将存活对象向一端移动，清除边界外内存。无碎片，但移动开销大。
4. **分代收集**：新生代用复制算法，老年代用标记-清除/标记-整理。

---

### 17. 新生代和老年代的比例？对象如何晋升老年代？

**答案**：
- 新生代:老年代 = 1:2（默认）
- 新生代中 Eden:From:To = 8:1:1

**对象晋升老年代的条件**：
1. 年龄达到阈值（默认15，每次Minor GC存活+1）
2. 大对象直接进入老年代（-XX:PretenureSizeThreshold）
3. 动态年龄判断：Survivor中相同年龄对象大小总和 > Survivor空间一半，≥该年龄的对象直接晋升
4. Minor GC后Survivor放不下，通过分配担保进入老年代

---

### 18. Minor GC、Major GC、Full GC的区别？

**答案**：
- Minor GC：新生代垃圾回收，频率高，速度快，暂停时间短
- Major GC：老年代垃圾回收，通常伴随一次Minor GC
- Full GC：整个堆（新生代+老年代+方法区）的回收，暂停时间长，应尽量避免

**Full GC触发条件**：
1. 老年代空间不足
2. 元空间不足
3. 显式调用System.gc()
4. 晋升担保失败（CMS）

---

### 19. 常见的垃圾收集器？

**答案**：

| 收集器 | 代 | 算法 | 特点 |
|--------|-----|------|------|
| Serial | 新生代 | 复制 | 单线程，简单高效，客户端模式 |
| ParNew | 新生代 | 复制 | Serial多线程版，配合CMS |
| Parallel Scavenge | 新生代 | 复制 | 吞吐量优先，自适应调节 |
| Serial Old | 老年代 | 标记-整理 | 单线程 |
| Parallel Old | 老年代 | 标记-整理 | 多线程，配合Parallel Scavenge |
| CMS | 老年代 | 标记-清除 | 低延迟，并发收集 |
| G1 | 全堆 | 标记-整理+复制 | 区域化，可预测停顿，JDK9默认 |
| ZGC | 全堆 | - | 超低延迟(<1ms)，JDK15正式 |

**CMS四个阶段**：初始标记(STW) → 并发标记 → 重新标记(STW) → 并发清除

**G1特点**：
- 将堆分为多个Region
- 可设置最大停顿时间(-XX:MaxGCPauseMillis)
- 优先回收垃圾最多的Region
- 整体标记-整理，局部复制算法

---

### 20. 类加载过程？

**答案**：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载

1. **加载**：通过类全限定名获取字节码，转换为方法区运行时数据结构，生成Class对象
2. **验证**：文件格式、元数据、字节码、符号引用验证
3. **准备**：为静态变量分配内存并赋零值（不是赋初始值）
4. **解析**：符号引用转为直接引用
5. **初始化**：执行<clinit>()方法，赋值静态变量，执行静态代码块

**初始化触发时机（主动引用）**：
- new对象、读写静态变量、调用静态方法
- 反射调用
- 初始化子类时先初始化父类
- 启动类（main方法所在类）

---

### 21. 双亲委派模型？

**答案**：

**类加载器层次**：
- Bootstrap ClassLoader：加载JAVA_HOME/lib（C++实现）
- Extension ClassLoader：加载JAVA_HOME/lib/ext
- Application ClassLoader：加载classpath下的类
- 自定义ClassLoader

**工作流程**：类加载请求先委托给父类加载器，父类无法加载时才自己加载。

**好处**：
1. 避免类重复加载
2. 安全，防止核心类被篡改（如自己写的java.lang.String不会被加载）

**打破双亲委派**：SPI机制（JDBC驱动）、Tomcat类加载器、OSGi

---

### 22. JVM调优参数有哪些？

**答案**：

| 参数 | 说明 |
|------|------|
| -Xms | 初始堆大小 |
| -Xmx | 最大堆大小 |
| -Xmn | 新生代大小 |
| -XX:SurvivorRatio | Eden:Survivor比例 |
| -XX:MaxTenuringThreshold | 晋升老年代年龄阈值 |
| -XX:+UseG1GC | 使用G1收集器 |
| -XX:MaxGCPauseMillis | G1最大停顿时间 |
| -XX:+PrintGCDetails | 打印GC详情 |
| -XX:MetaspaceSize | 元空间初始大小 |
| -XX:+HeapDumpOnOutOfMemoryError | OOM时dump堆 |

---

### 23. 什么是内存泄漏？Java中常见的内存泄漏场景？

**答案**：内存泄漏是指对象不再被使用，但GC无法回收，导致内存逐渐耗尽。

**常见场景**：
1. 静态集合类（static List/Map）持有对象引用
2. 各种连接（数据库连接、网络连接）未关闭
3. 监听器未注销
4. 内部类持有外部类引用（非静态内部类/匿名内部类）
5. ThreadLocal使用后未remove
6. 缓存无淘汰策略（如HashMap做缓存）
7. 单例对象持有大对象引用

---

### 24. OOM的几种情况？

**答案**：
1. **Java heap space**：堆内存不足，对象太多
2. **GC overhead limit exceeded**：GC时间占比超过98%且回收内存不足2%
3. **Metaspace**：元空间不足，类太多或动态生成类过多
4. **unable to create new native thread**：线程数过多，栈空间不足
5. **Direct buffer memory**：堆外内存不足（NIO）
6. **Requested array size exceeds VM limit**：数组大小超过JVM限制

---

## 三、异常体系

### 25. Java异常体系结构？

**答案**：

```
Throwable
├── Error（系统级错误，不可恢复，如OOM、StackOverflow）
└── Exception
    ├── RuntimeException（运行时异常，非受检，如NPE、ClassCast）
    └── 受检异常（Checked Exception，必须处理，如IOException、SQLException）
```

**受检异常**：编译期必须处理（try-catch或throws声明）
**非受检异常**：RuntimeException及其子类，编译期不强制处理

---

### 26. throw和throws的区别？

**答案**：
- throw：方法体内手动抛出异常对象
- throws：方法声明上声明该方法可能抛出的异常类型，交给调用者处理

---

### 27. finally中的代码一定会执行吗？

**答案**：不一定。以下情况不执行：
1. 调用System.exit()终止JVM
2. 线程被杀死
3. 程序所在的CPU被关闭

**经典题**：try中有return，finally中也有return，最终返回finally中的值。但finally中修改基本类型返回值不生效（因为返回值已暂存）。

---

> **本部分共27题**，涵盖Java基础、集合、JVM、异常体系。
