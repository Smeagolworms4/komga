package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.SearchOperator
import org.gotson.komga.oracle.OracleTest

class SearchOperatorOracleTest : OracleTest() {
  override fun cases() {
    func("equals@256") {
      case("same instance") { SearchOperator.IsNullT<Int>().let { it == it } }
      case("other instance") { SearchOperator.IsNullT<Int>() == SearchOperator.IsNullT<Int>() }
      case("other type parameter") { SearchOperator.IsNullT<Int>().equals(SearchOperator.IsNullT<String>()) }
      case("is not null") { SearchOperator.IsNullT<Int>().equals(SearchOperator.IsNotNullT<Int>()) }
      case("date is null object") { SearchOperator.IsNullT<Int>().equals(SearchOperator.IsNull) }
      case("null") { SearchOperator.IsNullT<Int>().equals(null) }
      case("in a list") { listOf<Any>(SearchOperator.IsNotNullT<Int>(), SearchOperator.IsNullT<Int>()).indexOf(SearchOperator.IsNullT<String>()) }
    }
    func("hashCode@262") {
      case("equal instances") { SearchOperator.IsNullT<Int>().hashCode() == SearchOperator.IsNullT<String>().hashCode() }
      case("differs from is not null") { SearchOperator.IsNullT<Int>().hashCode() == SearchOperator.IsNotNullT<Int>().hashCode() }
      case("set of instances") { setOf(SearchOperator.IsNullT<Int>(), SearchOperator.IsNullT<Int>()).size }
    }
    func("equals@270") {
      case("same instance") { SearchOperator.IsNotNullT<Int>().let { it == it } }
      case("other instance") { SearchOperator.IsNotNullT<Int>() == SearchOperator.IsNotNullT<Int>() }
      case("other type parameter") { SearchOperator.IsNotNullT<Int>().equals(SearchOperator.IsNotNullT<String>()) }
      case("is null") { SearchOperator.IsNotNullT<Int>().equals(SearchOperator.IsNullT<Int>()) }
      case("date is not null object") { SearchOperator.IsNotNullT<Int>().equals(SearchOperator.IsNotNull) }
      case("null") { SearchOperator.IsNotNullT<Int>().equals(null) }
    }
    func("hashCode@276") {
      case("equal instances") { SearchOperator.IsNotNullT<Int>().hashCode() == SearchOperator.IsNotNullT<Int>().hashCode() }
      case("set of instances") { setOf(SearchOperator.IsNotNullT<Int>(), SearchOperator.IsNotNullT<String>(), SearchOperator.IsNullT<Int>()).size }
    }
  }
}
