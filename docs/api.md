# LeaseHub API

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

同源 `/api`，JSON 会话接口。先读取 `GET /api/auth/csrf`，写请求带响应中的 header/token；登录后重新取令牌。错误使用 HTTP 状态和 message；401需重新登录，403无岗位权限，404无此记录或不可访问，409版本过期/重复编号，400业务条件不满足。外部集成接口和开放API密钥尚未实现。

## 身份

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/auth/csrf` | CSRF引导 |
| POST | `/api/auth/login` | username/password登录 |
| GET | `/api/auth/me` | 当前账号、权限、菜单和设置，无密码散列 |
| POST | `/api/auth/logout` | 撤销当前会话 |
| POST | `/api/auth/password` | oldPassword/newPassword，成功后重新登录 |
| GET | `/api/public/about` | 公开品牌及非商业授权 |

## 查询与写入

`GET /api/data/{resource}?page=1&size=20&search=&state=`；页大小1–100，默认20。返回 items/total/page/size。状态筛选适用于设备、合同、收款、成本、结算、风险。关键词为名称/序列号/凭据/数字编号，按资源处理。没有通过客户端 org_id 参数扩大范围的方式。

`GET /api/data/{resource}/{id}` 返回 record/items/events/transfers；后3项按业务类型提供设备历史、合同分期和交接、结算来源和转款、风险跟进。未确认合同提供分期预览，不持久化为应收。

`GET /api/lookup` 返回当前范围下的表单关联项，每类最多1000条；客户仅能读取字典和自己的合同名称。`GET /api/reports` 返回真实应收、核销、逾期、设备分布、审核结算、闲置和到期跟进，预警最多100条。`GET /api/export/{resource}` 导出当前账号范围内设备/合同/账单/收款/成本/结算全部记录，最多10000条，CSV防公式注入，不受页面当前关键词过滤。

金额请求为整数分，分成 hq_bps 为万分比；响应中的金额和金额汇总为精确十进制字符串，避免浏览器整数精度丢失。日期 yyyy-MM-dd，合同开始含/结束不含。UI用元输入并精确换算。

`POST /api/data/{resource}` 新增主数据或业务单据，响应id；`PUT /api/data/{resource}/{id}` 仅更新允许的主数据，须带version；费率只允许停用，变化新增版本，合同及财务明细不可直接覆盖。账号创建须强密码，role_id决定HQ/PARTNER/CUSTOMER，后两者须org_id，客户还须customer_id。同范围角色可更换；机构和客户归属固定。

resources：organizations/accounts/roles/dictionary/settings/menus/assets/customers/rates/contracts/bills/receipts/costs/settlements/risks/audit/transfers。账单、审计和转款由业务动作生成；settings/menus初始化，不允许随意新建。

## 状态动作

`POST /api/data/{resource}/{id}/{action}` 请求须包含version及note，其他字段如下。

| 资源 | action | 额外字段 / 参与方 |
| --- | --- | --- |
| assets | allocate | org_id，总部发起 |
| assets | accept | 合作商本人接收 |
| assets | maintain / repair | 可用设备维保 / 完成维保 |
| assets | recall / receive / dispose | 总部回收 / 接收 / 处置 |
| contracts | submit / cancel | 总部或合作商提交 / 取消草稿或待确认 |
| contracts | confirm | 合同客户本人确认、出账 |
| contracts | deliver | delivered_on，合作商登记真实交付 |
| contracts | accept-delivery | 客户确认接收 |
| contracts | return | return_on，合作商登记归还 |
| contracts | confirm-return | 客户确认后释放设备 |
| contracts | cancel-return | 合作商撤销待确认归还登记 |
| receipts / costs | approve / reject / reverse | 总部审核权限，结算锁定时不可冲正 |
| settlements | approve | 独立总部审核人，不可自审 |
| settlements | cancel / reverse | 撤销草稿 / 冲正未转款审核单 |
| settlements | pay / unpay | transferred_on/reference，人工转款 / 冲正登记 |
| risks | follow | next_on及跟进note |
| risks | close / reopen | 保存原因与跟进历史 |
| accounts | reset-password | password，总部管理员，撤销旧凭证会话 |

转款只是人工凭据台账；不调用银行，不应重复向银行付款。所有状态、对象范围、来源锁定、金额和版本均由后端复核。
