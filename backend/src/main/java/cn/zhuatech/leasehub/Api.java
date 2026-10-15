// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 同源业务接口；权限和对象范围由服务端统一验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final LeaseService service;

  public Api(LeaseService service) {
    this.service = service;
  }

  /** 公开品牌与授权信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/about")
  public Map<String, String> about() {
    return Map.of(
        "name",
        "LeaseHub",
        "company",
        "知华科技（上海如静知华信息科技有限公司）",
        "website",
        "https://www.zhuatech.cn/",
        "license",
        "源码公开、非商业使用；未经书面授权不得商用");
  }

  /** 经对象隔离的表单关联项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lookup")
  public Map<String, Object> lookup() {
    return service.lookup();
  }

  /** 真实应收、核销与设备风险统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Map<String, Object> report() {
    return publicMoney(service.report());
  }

  /** 权限范围内列表或新建单据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/data/{resource}")
  public Map<String, Object> list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String state) {
    return publicMoney(service.list(resource, page, size, search, state));
  }

  /** 验证对象归属后读取或编辑记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/data/{resource}/{id}")
  public Map<String, Object> detail(@PathVariable String resource, @PathVariable long id) {
    return publicMoney(service.detail(resource, id));
  }

  /** 权限范围内列表或新建单据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/data/{resource}")
  public Map<String, Object> create(
      @PathVariable String resource, @RequestBody Map<String, Object> body) {
    return service.create(resource, body);
  }

  /** 验证对象归属后读取或编辑记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/data/{resource}/{id}")
  public Map<String, Boolean> edit(
      @PathVariable String resource, @PathVariable long id, @RequestBody Map<String, Object> body) {
    return service.edit(resource, id, body);
  }

  /** 检查版本后办理业务状态动作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/data/{resource}/{id}/{action}")
  public Map<String, Boolean> action(
      @PathVariable String resource,
      @PathVariable long id,
      @PathVariable String action,
      @RequestBody Map<String, Object> body) {
    return service.action(resource, id, action, body);
  }

  /** 当前范围的安全CSV明细导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/export/{resource}")
  public ResponseEntity<String> export(@PathVariable String resource) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=leasehub-" + resource + ".csv")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(service.export(resource));
  }

  /** 整数分按十进制字符串返回，避免浏览器大额精度丢失。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static Map<String, Object> publicMoney(Map<String, Object> input) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (var e : input.entrySet()) {
      Object value = e.getValue();
      String key = e.getKey();
      if (value instanceof Number
          && (key.endsWith("_cents") || Set.of("billed", "collected", "overdue").contains(key)))
        value = value.toString();
      else if (value instanceof Map<?, ?> m) {
        Map<String, Object> nested = new LinkedHashMap<>();
        m.forEach((k, v) -> nested.put(k.toString(), v));
        value = publicMoney(nested);
      } else if (value instanceof List<?> rows) {
        List<Object> converted = new ArrayList<>();
        for (var row : rows) {
          if (row instanceof Map<?, ?> m) {
            Map<String, Object> nested = new LinkedHashMap<>();
            m.forEach((k, v) -> nested.put(k.toString(), v));
            converted.add(publicMoney(nested));
          } else converted.add(row);
        }
        value = converted;
      }
      out.put(key, value);
    }
    return out;
  }
}
