// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leasehub;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 返回可理解错误，不输出数据库原始约束或凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestControllerAdvice
public class Errors {
  @ExceptionHandler(Problem.class)
  ResponseEntity<?> problem(Problem p) {
    return ResponseEntity.status(p.status).body(Map.of("message", p.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409).body(Map.of("message", "编号已存在或关联数据无效"));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> invalid() {
    return ResponseEntity.badRequest().body(Map.of("message", "请求格式无效"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other() {
    return ResponseEntity.status(500).body(Map.of("message", "服务异常，请联系管理员并核对健康状态"));
  }
}
