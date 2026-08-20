INSERT IGNORE INTO knowledge_point (id, parent_id, topic, name, difficulty, description, sort_order) VALUES
(1, NULL, 'Java', 'Java 基础', 1, 'Java 语言基础', 0),
(2, NULL, 'Redis', 'Redis', 1, 'Redis 整体', 0),
(3, NULL, 'MySQL', 'MySQL', 1, 'MySQL 整体', 0),
(4, NULL, 'JVM', 'JVM', 1, 'JVM 整体', 0),
(5, NULL, '并发', '并发编程', 1, '并发编程整体', 0),

(11, 1, 'Java', '集合框架', 2, 'ArrayList/HashMap/HashSet 底层原理', 1),
(12, 1, 'Java', '泛型与反射', 2, '泛型擦除、反射机制', 2),
(13, 1, 'Java', '异常体系', 1, '受检异常与运行时异常', 3),
(14, 1, 'Java', 'IO 与 NIO', 3, 'BIO/NIO/AIO、零拷贝', 4),
(15, 1, 'Java', 'Java 新特性', 2, 'Lambda/Stream/虚拟线程', 5),

(21, 2, 'Redis', '数据结构', 2, 'String/Hash/List/Set/ZSet 底层', 1),
(22, 2, 'Redis', '持久化', 2, 'RDB/AOF 原理与对比', 2),
(23, 2, 'Redis', '缓存设计', 2, '穿透/击穿/雪崩、一致性', 3),
(24, 2, 'Redis', '线程模型与 IO 多路复用', 3, '单线程模型、Reactor、epoll', 4),
(25, 2, 'Redis', '高可用与集群', 3, '哨兵、Cluster、主从复制', 5),

(31, 3, 'MySQL', '索引', 2, 'B+ 树、聚簇索引、回表', 1),
(32, 3, 'MySQL', '事务与隔离级别', 2, 'ACID、四种隔离级别', 2),
(33, 3, 'MySQL', '锁与 MVCC', 3, '行锁、间隙锁、undo log', 3),
(34, 3, 'MySQL', 'SQL 优化', 2, '执行计划、慢查询优化', 4),
(35, 3, 'MySQL', '主从复制', 3, 'binlog、半同步复制', 5),

(41, 4, 'JVM', '内存区域', 2, '堆/栈/方法区、OOM 场景', 1),
(42, 4, 'JVM', 'GC 算法与收集器', 3, '标记清除/复制、G1、ZGC', 2),
(43, 4, 'JVM', '类加载机制', 2, '双亲委派、打破双亲委派', 3),
(44, 4, 'JVM', '性能调优', 3, 'JVM 参数、GC 日志分析', 4),

(51, 5, '并发', '线程基础', 1, '线程生命周期、创建方式', 1),
(52, 5, '并发', 'synchronized 与锁', 2, 'Monitor、锁升级、锁消除', 2),
(53, 5, '并发', 'volatile 与内存可见性', 2, 'JMM、happens-before', 3),
(54, 5, '并发', 'JUC 工具类', 2, 'CountDownLatch/CyclicBarrier/Semaphore', 4),
(55, 5, '并发', '线程池', 3, 'ThreadPoolExecutor 原理、拒绝策略', 5),
(56, 5, '并发', 'AQS', 3, 'AQS 原理、ReentrantLock', 6);