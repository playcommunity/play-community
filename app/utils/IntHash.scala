package utils

import java.nio.charset.Charset

/**
 * hash qq openId
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-28
 */
object IntHash extends App {


  import java.security.{MessageDigest, NoSuchAlgorithmException}

  private val charset: Charset = Charset.forName("UTF-8") // 用于将哈希值存储为字符串的编码

  private val hashName: String = "MD5" // MD5在大多数情况下具有足够的准确度如果需要的话换成sha1
  private var tmp: MessageDigest = _

  try {
    tmp = java.security.MessageDigest.getInstance(hashName)
  } catch {
    case e: NoSuchAlgorithmException =>
      tmp = null
  }
  private val digestFunction = tmp


  /**
   * 使用 布隆过滤器算法，将字符串的字节数组进行一次hash，绝对值整数作为QQ用户的账号id
   *
   * @param s openId
   * @param hashes 为了简单只hash一次
   * @return
   */
  def hashOpenId(s: String, hashes: Int = 1): Int = {
    val data = s.getBytes(charset)
    val result = new Array[Int](hashes)
    var k: Int = 0
    var salt: Byte = 0
    while (k < hashes) {
      var digest: Array[Byte] = null
      digestFunction synchronized {
        digestFunction.update(salt)
        salt = (salt.toInt + 1).toByte
        digest = digestFunction.digest(data)
      }
      for (i <- 0 until digest.length / 4 if k < hashes) {
        var h = 0
        for (j <- (i * 4) until (i * 4) + 4) {
          h <<= 8
          h |= digest(j).asInstanceOf[Int] & 0xFF
        }
        result(k) = h
        k += 1
      }
    }
    Math.abs(result(0))
  }
}
