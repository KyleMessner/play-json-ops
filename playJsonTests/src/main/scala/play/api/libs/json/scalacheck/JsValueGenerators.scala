package play.api.libs.json.scalacheck

import org.scalacheck.Shrink._
import org.scalacheck.{Shrink, Arbitrary, Gen}
import play.api.libs.json._

import scala.language.implicitConversions

trait JsValueGenerators {

  /**
   * The maximum number of fields of a [[JsObject]] or elements of a [[JsArray]] to construct when
   * generating one of these nested [[JsValue]]s.
   */
  def defaultMaxWidth: Width = Width(2)

  /**
   * The maximum number of levels deep where nested values ([[JsObject]]s or [[JsArray]]s) can be generated.
   *
   * In other words:
   * - A depth of 0 generates only primitive [[JsValue]]s
   * - A depth of 1 generates any type of [[JsValue]] where all nested values contain only primitive [[JsValue]]s.
   * - A depth of n generates any type of [[JsValue]] where all nested values contain [[JsValue]]s with a depth of n - 1
   */
  def defaultMaxDepth: Depth = Depth(2)

  implicit def arbJsValue(implicit
    maxDepth: Depth = defaultMaxDepth,
    maxWidth: Width = defaultMaxWidth): Arbitrary[JsValue] = Arbitrary(genJsValue)

  implicit def arbJsObject(implicit
    maxDepth: Depth = defaultMaxDepth,
    maxWidth: Width = defaultMaxWidth): Arbitrary[JsObject] = Arbitrary(genJsObject)

  implicit def arbJsArray(implicit
    maxDepth: Depth = defaultMaxDepth,
    maxWidth: Width = defaultMaxWidth): Arbitrary[JsArray] = Arbitrary(genJsArray)

  implicit def arbJsString(implicit arbString: Arbitrary[String]): Arbitrary[JsString] = Arbitrary {
    arbString.arbitrary map JsString
  }

  implicit def arbJsNumber(implicit arbBigDec: Arbitrary[BigDecimal]): Arbitrary[JsNumber] = Arbitrary {
    arbBigDec.arbitrary map JsNumber
  }

  implicit def arbJsBoolean: Arbitrary[JsBoolean] = Arbitrary {
    Gen.oneOf(true, false) map JsBoolean
  }

  /**
   * Generates non-nested [[JsValue]]s (ie. not [[JsArray]] or [[JsObject]]).
   *
   * @note this will produce [[JsUndefined]]
   */
  def genJsPrimitive: Gen[JsValue] = {
    Gen.oneOf(
      arbJsBoolean.arbitrary,
      arbJsNumber.arbitrary,
      arbJsString.arbitrary,
      Gen.const(JsNull),
      Gen.const(JsUndefined("[generated by genJsPrimitive]"))
    )
  }

  /**
   * Generates a primitive or nested [[JsValue]] up to the specified depth and width
   *
   * @param maxDepth see [[defaultMaxDepth]] (cannot be less than 0)
   * @param maxWidth see [[defaultMaxWidth]] (cannot be less than 0)
   */
  def genJsValue(implicit maxDepth: Depth = defaultMaxDepth, maxWidth: Width = defaultMaxWidth): Gen[JsValue] = {
    if (maxDepth === 0) genJsPrimitive
    else Gen.oneOf(
      genJsPrimitive,
      // The Scala compiler has a bug with AnyVal, where it favors implicits in the outer scope
      genJsArray(maxDepth, maxWidth),
      genJsObject(maxDepth, maxWidth)
    )
  }

  /**
   * Generates a nested array at the specified depth and width.
   *
   * @note the arrays may contain mixed type values at different depths, but never deeper than the [[defaultMaxDepth]].
   *
   * @param maxDepth see [[defaultMaxDepth]] (cannot be less than 1)
   * @param maxWidth see [[defaultMaxWidth]] (cannot be less than 1)
   */
  def genJsArray(implicit maxDepth: Depth = defaultMaxDepth, maxWidth: Width = defaultMaxWidth): Gen[JsArray] =
    Gen.listOfN(maxWidth, genJsValue(maxDepth - 1, maxWidth)) map { JsArray(_) }

  /**
   * Generates a valid field name where the first character is alphabetical and the remaining chars
   * are alphanumeric.
   */
  def genFieldName: Gen[String] = Gen.identifier

  def genFields(implicit maxDepth: Depth = defaultMaxDepth, maxWidth: Width = defaultMaxWidth): Gen[(String, JsValue)] = {
    // The Scala compiler has a bug with AnyVal, where it favors implicits in the outer scope
    Gen.zip(genFieldName, genJsValue(maxDepth, maxWidth))
  }

  /**
   * Generates a nested array at the specified depth and width.
   *
   * @param maxDepth see [[defaultMaxDepth]] (cannot be less than 1)
   * @param maxWidth see [[defaultMaxWidth]] (cannot be less than 1)
   */
  def genJsObject(implicit maxDepth: Depth = defaultMaxDepth, maxWidth: Width = defaultMaxWidth): Gen[JsObject] = {
    for {
      fields <- Gen.listOfN(maxWidth, genFields(maxDepth - 1, maxWidth))
    } yield JsObject(fields)
  }

  // Shrinks for better error output

  implicit val shrinkJsArray: Shrink[JsArray] = Shrink {
    arr =>
      val stream: Stream[JsArray] = shrink(arr.value) map JsArray
      stream
  }

  implicit val shrinkJsObject: Shrink[JsObject] = Shrink {
    obj =>
      val stream: Stream[JsObject] = shrink(obj.value) map { fields => JsObject(fields.toSeq) }
      stream
  }

  implicit val shrinkJsValue: Shrink[JsValue] = Shrink {
    case array: JsArray => shrink(array)
    case obj: JsObject  => shrink(obj)
    case JsString(str)  => shrink(str) map JsString
    case JsNumber(num)  => shrink(num) map JsNumber
    case JsNull | JsUndefined() | JsBoolean(_) => Stream.empty[JsValue]
  }
}
