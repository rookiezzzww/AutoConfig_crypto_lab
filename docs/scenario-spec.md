# scenario.yml 规范

每个场景目录至少包含：

```text
Dockerfile
start.sh
scenario.yml
vulnerability.env
```

核心声明示例：

```yaml
id: example
name: Example
cve: CVE-YYYY-NNNN
description: 场景说明
category: [TLS]
runtime:
  image: crypto-lab/example:1.0
  containerName: crypto-example
  containerPort: 8443
  protocol: tcp
  backend: example
switch:
  mode: ondemand
healthCheck: { type: tcp, port: 8443 }
verification: { type: tls }
vulnerabilityOptions:
  - key: cipherMode
    name: 密码套件
    description: 仅描述 CVE 复现所需的密码误用条件
    type: enum
    envVar: EXAMPLE_CIPHER_MODE
    defaultValue: weak
    vulnerableValue: weak
    allowedValues: [weak, strong]
    sensitive: false
    restartRequired: true
enabled: true
```

对应的 `vulnerability.env`：

```dotenv
EXAMPLE_CIPHER_MODE=weak
```

字段约束：

- `id` 必须全局唯一。
- `runtime.backend` 必须与 `haproxy.cfg` 的 server 名一致。
- `key` 和 `envVar` 在单个场景内必须唯一。
- `vulnerability.env` 不允许出现未声明变量。
- 有 `allowedValues` 时，有效值必须属于该白名单。
- `vulnerableValue` 表示满足 CVE 密码误用条件的值。
- 当多个值都满足条件时，可用 `vulnerableValues` 列表替代 `vulnerableValue`。
- `restartRequired` 表示参数通过弹窗修改后需重建该漏洞容器；控制面自动完成重建，不重启自身或 HAProxy。
- `sensitive: true` 用于未来的密钥、IV 等敏感项，控制面 API 会返回掩码。

容器启动脚本必须再次使用 `case` 或等效白名单校验，不能把环境变量直接拼接成 shell 命令。新增场景不需要修改核心 Java 代码，但需要在 Compose 中添加带 `manual-scenarios` profile 的场景服务，并在 HAProxy backend 中添加带 Docker DNS resolver、初始为 `disabled` 的 server。控制面会在第一次初始化时按需构建镜像。
