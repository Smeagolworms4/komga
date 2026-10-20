package org.gotson.komga.oracle.interfaces.api.rest.v3

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.gotson.komga.interfaces.api.rest.dto.PasswordUpdateDto
import org.gotson.komga.interfaces.api.rest.v3.ApiV3ErrorHandlingControllerAdvice
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.core.MethodParameter
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class ApiV3ErrorHandlingControllerAdviceOracleTest : OracleTest() {
  private val advice = ApiV3ErrorHandlingControllerAdvice()
  private val validator = Validation.buildDefaultValidatorFactory().validator
  private val method = ApiV3ErrorHandlingControllerAdvice::class.java.getMethod("onConstraintValidationException", ConstraintViolationException::class.java)

  private fun notValid(vararg errors: Pair<String, String?>): MethodArgumentNotValidException {
    val result = BeanPropertyBindingResult(PasswordUpdateDto("x"), "passwordUpdateDto")
    errors.forEach { (f, m) -> result.addError(FieldError("passwordUpdateDto", f, "rejected", false, null, null, m)) }
    return MethodArgumentNotValidException(MethodParameter(method, -1), result)
  }

  private fun problem(p: ProblemDetail?) = p?.let { listOf(it.type.toString(), it.title, it.status, it.detail, it.instance, it.properties) }

  private fun handle(ex: Exception): Any? {
    val r: ResponseEntity<Any>? = advice.handleException(ex, ServletWebRequest(WebOracle.request(uri = "/api/v3/notable-entities")))
    return r?.let { WebOracle.describeEntity(it).let { d -> listOf(d[0], d[1], problem(it.body as ProblemDetail?)) } }
  }

  override fun cases() {
    func("handleMethodArgumentNotValid") {
      case("no error") { handle(notValid()) }
      case("errors in order") { handle(notValid("password" to "must not be blank", "other" to null)) }
    }
    func("onConstraintValidationException") {
      case("no violation") { problem(advice.onConstraintValidationException(ConstraintViolationException(emptySet()))) }
      case("one violation") { problem(advice.onConstraintValidationException(ConstraintViolationException(validator.validate(PasswordUpdateDto(""))))) }
    }
    func("handleException") {
      case("type mismatch") { handle(MethodArgumentTypeMismatchException("abc", Boolean::class.java, "unpaged", MethodParameter(method, -1), null)) }
      case("missing parameter") { handle(MissingServletRequestParameterException("search", "String")) }
    }
  }
}
