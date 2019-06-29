package utils

/**
 * 不满条件抛出异常
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-27
 */
object IsCondition {

  /**
   * 满足条件就抛出通用异常
   *
   * @param condition
   * @param msg   异常说明
   * @param cause 异常原因 默认空
   */
  @throws[Exception]
  def conditionException(condition: => Boolean, msg: String = "default message info", cause: Throwable = new Exception("default exception")) = {
    if (condition) {
      if (cause != null) {
        throw new Exception(s"Not satisfying the conditions because : {$msg}", cause)
      }
      throw new Exception(s"Not satisfying the conditions because : {$msg}")
    }
  }
}
