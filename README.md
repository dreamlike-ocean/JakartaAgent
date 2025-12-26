# Javax to Jakarta Transformer

一个用于扫描和转换Java Web应用程序中Javax到Jakarta EE规范的工具集。本项目包含两个主要模块：servlet scanner（用于扫描Javax依赖）和transformer（用于运行时转换Javax到Jakarta）。

## 模块介绍

### 1. Servlet Scanner 模块

Servlet Scanner 是一个用于扫描JAR包或类文件中javax.servlet相关依赖的工具。它可以分析类文件中的类、方法、字段等元素，找出所有使用javax.servlet API的位置，帮助开发者了解项目中需要转换的Javax依赖。

#### 构建逻辑

- **普通构建**：运行 `mvn clean package` 将生成一个可执行的JAR文件 至少需要jdk25
- **Native构建**：需要满足两个条件才能构建Native镜像：
  1. 系统中必须安装GraalVM native-image工具
  2. 启动相关 maven profile

#### Native构建参数

要构建Native版本，使用以下命令：

```bash
# 发布版Native构建（优化级别-O3）
mvn clean package -Pnative-release

# 调试版Native构建（优化级别-Ob）
mvn clean package -Pnative-debug
```

#### 主函数入参说明

```bash
java -jar javax-servlet-scanner-[version].jar <jar路径> [详情类型]
```

参数说明：
- `<jar路径>`：必需参数，指定要扫描的JAR文件路径
- `[详情类型]`：可选参数，控制输出详细程度，可选值：
  - `Jar`：仅输出包含Javax依赖的JAR文件名
  - `Class`：输出包含Javax依赖的JAR文件名和类名
  - `ALL`：输出所有详细信息（默认值）

#### 使用用例

**用例1：扫描WAR包中的Javax依赖（仅显示JAR文件名）**
```bash
java -jar javax-servlet-scanner-1.0-SNAPSHOT.jar myapp.war Jar
```

**用例2：扫描JAR包中的Javax依赖（显示JAR和类名）**
```bash
java -jar javax-servlet-scanner-1.0-SNAPSHOT.jar mylibrary.jar Class
```

**用例3：完整扫描JAR包（显示所有详细信息）**
```bash
java -jar javax-servlet-scanner-1.0-SNAPSHOT.jar myapp.jar ALL
```


#### 扫描范围和位置

Servlet Scanner 会扫描 class 文件中所有可能引用到 `javax.servlet.*` 的位置，覆盖以下范围：

##### **类级别（Class）**
- **父类（super class）**：继承了 javax.servlet 相关类
- **接口（interfaces）**：实现了 javax.servlet 相关接口
- **类泛型签名（Signature）**：class-level generics 中包含 javax.servlet
- **类注解（Annotations）**：类上的注解引用 javax.servlet
- **类型注解（Type Annotations）**：类级别的类型注解引用 javax.servlet
- **Record 组件（Record Components）**：
    - 组件类型包含 javax.servlet
    - 泛型签名包含 javax.servlet
    - 注解包含 javax.servlet

##### **字段级别（Field）**
- **字段类型（descriptor）** 使用 javax.servlet 类型
- **字段泛型（Signature）** 包含 javax.servlet
- **字段注解（Annotations）** 包含 javax.servlet
- **字段类型注解（Type Annotations）** 包含 javax.servlet

##### **方法级别（Method）**

**方法声明部分：**
- **方法泛型（Signature）** 包含 javax.servlet
- **参数类型** 使用 javax.servlet
- **返回类型** 使用 javax.servlet
- **throws 声明** 中包含 javax.servlet 异常类型
- **方法注解（Annotations）** 包含 javax.servlet
- **方法类型注解（Type Annotations）** 包含 javax.servlet
- **方法参数注解（Parameter Annotations）** 包含 javax.servlet

**方法体（Method Body）内指令扫描：**
- **new / anewarray**：创建 javax.servlet 类或数组
- **checkcast / instanceof**：涉及 javax.servlet 类型
- **字段访问（getfield/putfield）**：字段类型引用 javax.servlet
- **方法调用（invokevirtual/invokestatic/...）**
    - owner 为 javax.servlet
    - 方法描述符中引用 javax.servlet
- **invokedynamic**
    - bootstrap 方法 handle 中引用 javax.servlet
    - 方法类型中引用 javax.servlet
    - bootstrapArgs 中包含 javax.servlet 类型或字符串
- **LDC 指令**
    - 常量为 javax.servlet 相关类（ClassEntry）
    - 常量字符串包含 "javax.servlet"
    - condy（ConstantDynamic）中引用 javax.servlet
- **多维数组（multianewarray）** 使用 javax.servlet 类型
- **try/catch** 中 catch 类型引用 javax.servlet
- **局部变量表（LocalVariable）** 的类型或签名包含 javax.servlet
- **栈帧（StackMapFrame）** 中包含 javax.servlet 类型

##### **总结**
Servlet Scanner 的扫描范围非常完整，覆盖所有 JVM classfile 中可能出现 javax.servlet 的结构，包括：
- 原始类型（descriptor）
- 泛型（signature）
- 注解
- 字节码指令
- invokedynamic 与 condy
- record component
- 字段、方法、局部变量、异常、栈帧

确保能够精准发现所有直接或间接使用 javax.servlet API 的位置。

### 2. Transformer 模块

Transformer 是一个Java Agent，用于在运行时将Javax API调用转换为Jakarta EE API调用。它使用字节码操作技术（ASM）来修改类文件，实现Javax到Jakarta的自动转换。

#### 工作原理

Transformer通过Java Instrumentation API在类加载时修改字节码，将所有javax.*包的引用重定向到对应的jakarta.*包。它使用ASM库来解析和修改类文件，确保在运行时无缝转换API调用。

#### 转换目标

- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.validation.*` → `jakarta.validation.*`

#### 具体位置

Transformer 会处理所有 class 文件中可能引用到 `javax.*` 的位置，覆盖范围包括：

##### **类级别（Class）**
- 超类（super class）中引用 javax
- 实现的接口中引用 javax
- 类的泛型签名（Signature）中的 javax
- 类注解中的 javax
- 类的类型注解中的 javax
- Record 组件的类型、签名、注解中的 javax

##### **字段级别（Field）**
- 字段类型（descriptor）引用 javax
- 字段的泛型签名中包含 javax
- 字段注解中包含 javax
- 字段类型注解中包含 javax

##### **方法级别（Method）**

**方法声明部分：**
- 方法参数类型引用 javax
- 方法返回类型引用 javax
- 方法 throws 异常类型引用 javax
- 方法的泛型签名中引用 javax
- 方法注解引用 javax
- 方法类型注解引用 javax
- 方法参数注解引用 javax

**方法体指令部分（Instructions）：**
- NEW / CHECKCAST / INSTANCEOF / ANEWARRAY 等类型相关指令引用 javax
- 字段访问指令（FieldInsn）中 owner/desc 引用 javax
- 方法调用指令（MethodInsn）中 owner/desc 引用 javax
- invokedynamic 指令：
    - bootstrap method handle 引用 javax
    - 方法 desc 引用 javax
    - bsmArgs 中 Type/Handle 引用 javax
- LDC 指令：
    - `LDC Type` 中 descriptor 含 javax
    - `LDC "javax.xxx.Foo"` 的字符串字面量
- MULTIANEWARRAY 指令引用 javax
- try/catch 异常类型引用 javax
- 局部变量表（LocalVariable）中的类型或签名引用 javax
- 栈帧（StackMapFrame）中的类型引用 javax

##### **Record Component**
- 类型引用 javax
- 泛型签名引用 javax
- 注解引用 javax
- 类型注解引用 javax

#### Agent启动参数

可以通过以下JVM参数来配置Transformer Agent：

```bash
java -javaagent:javax-to-jakarta-transformer-[version].jar=[参数] -jar yourapp.jar
```

参数格式：`key1=value1,key2=value2`

可用参数：
- `jakarta.dump.path`：指定转换后类文件的输出路径（用于调试）
- `jakarta.compute.frames.fast`：是否使用快速帧计算模式（默认false）

#### Agent使用用例

**用例1：基本使用（无额外参数）**
```bash
java -javaagent:javax-to-jakarta-transformer-1.0-SNAPSHOT.jar -jar myapp.jar
```

**用例2：启用调试模式（输出转换后的类文件）**
```bash
java -javaagent:javax-to-jakarta-transformer-1.0-SNAPSHOT.jar=jakarta.dump.path=/tmp/dump -jar myapp.jar
```

**用例3：启用快速计算模式**
```bash
java -javaagent:javax-to-jakarta-transformer-1.0-SNAPSHOT.jar=jakarta.compute.frames.fast=true -jar myapp.jar
```

**用例4：同时使用多个参数**
```bash
java -javaagent:javax-to-jakarta-transformer-1.0-SNAPSHOT.jar=jakarta.dump.path=/tmp/dump,jakarta.compute.frames.fast=true -jar myapp.jar
```

## 构建方式

### 环境要求

- JDK 17 或更高版本
- Maven 3.6.0 或更高版本
- （可选）GraalVM native-image（用于构建Native版本）

### 构建步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd JavaxTransformer
   ```

2. **构建所有模块**
   ```bash
   mvn clean package
   ```

3. **构建特定模块**
   ```bash
   # 仅构建servlet scanner
   mvn clean package -pl javax-servlet-scanner
   
   # 仅构建transformer
   mvn clean package -pl javax-to-jakarta-transformer
   ```

4. **构建Native版本（需要GraalVM）**
   ```bash
   # 构建servlet scanner的Native版本
   mvn clean package -Pnative-release -pl javax-servlet-scanner
   ```

### 输出文件

构建完成后，可在各模块的 `target/` 目录下找到：
- `javax-servlet-scanner-[version].jar` - Servlet Scanner可执行JAR
- `javax-servlet-scanner-native` - Native版本（如果构建了Native镜像）
- `javax-to-jakarta-transformer-[version].jar` - Transformer Agent JAR

## 使用场景

本工具集特别适用于：
- 将旧的Javax EE应用迁移到新的Jakarta EE平台
- 在不修改源代码的情况下运行依赖Javax的旧应用
- 分析项目中Javax依赖的使用情况
- 快速测试应用的Jakarta兼容性

## 注意事项

- Transformer Agent仅在类加载时进行转换，已加载的类不会被转换
- 某些使用反射访问Javax API的代码可能需要额外处理
- Native构建需要较长时间，请耐心等待