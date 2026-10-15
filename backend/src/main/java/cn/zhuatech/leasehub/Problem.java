// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

/** 可公开的业务错误，无敏感字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class Problem extends RuntimeException {
  final int status;

  public Problem(int status, String message) {
    super(message);
    this.status = status;
  }
}
