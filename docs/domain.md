# 领域文档与术语规范 (Domain Docs)

本文档定义 RePoi 项目的业务领域模型概念与术语契约。智能体在命名新特性、撰写提案或测试用例时，必须严格遵从本约定。

---

## 1. 核心领域概念对照 (Vocabulary)

| 领域术语 | 英文标识 | 概念定义与业务范围 | 典型实体/模型 |
|---|---|---|---|
| **手办/模型** | `Hobby` | 核心收藏实体，包含标准手办、粘土人、比例模型、盒蛋等条目。 | `HobbyItem`, `HobbyDetailData` |
| **周边/衍生** | `ExHobby` | 衍生品、模型配件、一番赏、谷子及扩展图库条目。 | `ExHobbyItem`, `ExHobbyDetail` |
| **作品/原作** | `Works` | 角色与手办来源的原作企划、动漫、游戏或小说。 | `WorksItem`, `WorksDetailData` |
| **角色/人物** | `Character` / `Person` | 条目登场人物原型（`Character`）或声优/原型师（`Person`）。 | `CharacterData`, `PersonDetailData` |
| **厂商/品牌** | `Vendor` | 手办制作商、发行商（支持厂商屏蔽过滤）。 | `VendorDetailData`, `VendorItem` |
| **相册广场** | `Album` | 用户摄影晒单、官方开箱图集。大屏端支持主内容与评论双栏。 | `AlbumItem`, `AlbumDetailData` |
| **专区/圈子** | `Zone` | 特定 IP 或主题聚合专区（如初音未来、东方 Project 专区）。 | `ZoneItem`, `ZoneDetailData` |
| **讨论板/帖子** | `Forum` / `Post` | 社区发帖、问答与二级回帖交流。 | `PostItem`, `PostDetailData` |
| **二手交易** | `Resale` | 用户手办二手转让行情、发帖求购与地域筛选。 | `ResaleItem`, `ResaleDetailData` |
| **收藏管理** | `Collect` | 用户收藏资产（心愿想买、预定在途、已入柜、已出转）。 | `CollectItem`, `CollectState` |

---

## 2. 架构决策记录规范 (ADR)

- 重大架构演进、核心选型对比及全局变更均记录于 `docs/adr/`。
- 新增决策提案时，遵循 `docs/adr/README.md` 模板，创建版本化编号文档（如 `0002-xxx.md`）。
- 任何产出与既有 ADR 冲突时，必须显式标注冲突项并进行说明。
