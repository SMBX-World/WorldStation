package ink.chyk.worldstation.dto

data class LoginRequestDTO(
    val identification: String = "",
    val password: String = "",
) {
    // 避免密码被意外写入日志
    override fun toString(): String = "LoginRequestDTO(identification=$identification, password=***)"
}
