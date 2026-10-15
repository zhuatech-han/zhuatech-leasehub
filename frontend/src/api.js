// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let token;
/** 同源 CSRF 请求客户端。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function request(path, method = "GET", body) {
  if (method !== "GET" && !token) token = await request("/auth/csrf");
  const res = await fetch("/api" + path, {
    method,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(method !== "GET" ? { [token.header]: token.token } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const e = new Error(
      data.message ||
        { 401: "请重新登录", 403: "操作权限不足，请刷新后重试" }[res.status] ||
        "请求失败",
    );
    e.status = res.status;
    throw e;
  }
  if (path.startsWith("/auth/login") || path.startsWith("/auth/logout"))
    token = undefined;
  return data;
}
