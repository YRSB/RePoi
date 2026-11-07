# RePoi

RePoi 是基于 Kotlin Multiplatform 的 Hpoi 跨平台客户端，提供手办模型维基、个人收藏与社区交流服务。

## Language

**登录令牌 (Login Token / utoken)**:
Hpoi 平台用于标识已验证用户会话凭据的唯一身份令牌，作为请求鉴权凭证。
_Avoid_: 会话密码, 临时密钥, Cookie (除非特指浏览器存储形式)

**Token 登录 (Token Login)**:
用户直接输入或导入有效登录令牌以恢复已登录会话的认证方式。
_Avoid_: 快捷免密登录, 无密码登录

**Token 导出 (Token Export)**:
将当前设备已认证的登录令牌导出或复制到剪贴板，用于跨设备或多端会话迁移。
_Avoid_: 密码导出, 账号导出
