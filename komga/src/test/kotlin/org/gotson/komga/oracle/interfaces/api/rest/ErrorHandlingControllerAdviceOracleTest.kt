package org.gotson.komga.oracle.interfaces.api.rest

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import org.gotson.komga.domain.model.EntityNotFoundException
import org.gotson.komga.interfaces.api.rest.ErrorHandlingControllerAdvice
import org.gotson.komga.interfaces.api.rest.dto.PasswordUpdateDto
import org.gotson.komga.oracle.OracleTest
import org.springframework.core.MethodParameter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.multipart.MaxUploadSizeExceededException

class ErrorHandlingControllerAdviceOracleTest : OracleTest() {
  private val advice = ErrorHandlingControllerAdvice()
  private val validator = Validation.buildDefaultValidatorFactory().validator

  private fun notValid(vararg errors: Pair<String, String?>): MethodArgumentNotValidException {
    val result = BeanPropertyBindingResult(PasswordUpdateDto("x"), "passwordUpdateDto")
    errors.forEach { (f, m) -> result.addError(FieldError("passwordUpdateDto", f, "rejected", false, null, null, m)) }
    val method = ErrorHandlingControllerAdvice::class.java.getMethod("handleEntityNotFound")
    return MethodArgumentNotValidException(MethodParameter(method, -1), result)
  }

  override fun cases() {
    func("onConstraintValidationException") {
      case("no violation") { advice.onConstraintValidationException(ConstraintViolationException(emptySet())) }
      case("one violation") {
        advice.onConstraintValidationException(ConstraintViolationException(validator.validate(PasswordUpdateDto(""))))
      }
    }
    func("onMethodArgumentNotValidException") {
      case("no error") { advice.onMethodArgumentNotValidException(notValid()) }
      case("errors in order") { advice.onMethodArgumentNotValidException(notValid("password" to "must not be blank", "other" to null)) }
    }
    func("handleEntityNotFound") {
      case("unit") { advice.handleEntityNotFound() }
    }
    func("handleMaxUploadSizeExceededException") {
      case("known size") {
        advice.handleMaxUploadSizeExceededException(MaxUploadSizeExceededException(1024)).let {
          listOf(it.type.toString(), it.title, it.status, it.detail, it.instance, it.properties)
        }
      }
      case("unknown size") {
        advice.handleMaxUploadSizeExceededException(MaxUploadSizeExceededException(-1)).let {
          listOf(it.type.toString(), it.title, it.status, it.detail, it.instance, it.properties)
        }
      }
    }
  }
}
