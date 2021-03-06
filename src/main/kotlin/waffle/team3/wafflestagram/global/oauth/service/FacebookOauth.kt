package waffle.team3.wafflestagram.global.oauth.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import waffle.team3.wafflestagram.domain.User.model.SignupType
import waffle.team3.wafflestagram.domain.User.model.User
import waffle.team3.wafflestagram.domain.User.repository.UserRepository
import waffle.team3.wafflestagram.global.oauth.OauthToken
import waffle.team3.wafflestagram.global.oauth.exception.AccessTokenException

@Component
class FacebookOauth(
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
) : SocialOauth {
    @Value("\${facebook.url}")
    private val facebook_base_auth_url: String? = null

    @Value("\${facebook.client.id}")
    private val facebook_client_id: String? = null

    @Value("\${facebook.client.secret}")
    private val facebook_client_secret: String? = null

    @Value("\${facebook.callback.url}")
    private val facebook_callback_url: String? = null

    @Value("\${facebook.token.url}")
    private val facebook_base_token_auth_url: String? = null

    @Value("\${facebook.info.url}")
    private val facebook_userinfo_url: String? = null

    @Override
    override fun getOauthRedirectURL(): String {
        val paramMap = mutableMapOf<String, String?>()
        paramMap.put("scope", "email")
        paramMap.put("response_type", "code")
        paramMap.put("client_id", facebook_client_id)
        paramMap.put("redirect_uri", facebook_callback_url)
        val parameterString = paramMap.map { it.key + '=' + it.value }.joinToString("&")
        return facebook_base_auth_url + "?" + parameterString
    }

    @Override
    override fun requestAccessToken(code: String): OauthToken {
        val restTemplate = RestTemplateBuilder().build()
        val paramMap = mutableMapOf<String, String?>()
        paramMap.put("code", code)
        paramMap.put("client_id", facebook_client_id)
        paramMap.put("client_secret", facebook_client_secret)
        paramMap.put("redirect_uri", facebook_callback_url)
        paramMap.put("grant_type", "authorization_code")

        val responseEntity = restTemplate.postForEntity(facebook_base_token_auth_url!!, paramMap, String::class.java)
        if (responseEntity.statusCode == HttpStatus.OK) {
            val hashmap = objectMapper.readValue(responseEntity.body, HashMap::class.java)
            println(hashmap)
            return OauthToken(
                access_token = hashmap["access_token"].toString(),
                expires_in = hashmap["expires_in"].toString().toLong(),
                scope = hashmap["scope"].toString(),
                token_type = hashmap["token_type"].toString(),
                id_token = hashmap["id_token"].toString()
            )
        } else throw AccessTokenException("Access token Failed")
    }

    @Value("\${cloud.aws.s3.photoURL_default}")
    lateinit var default_s3URL: String

    @Override
    override fun findUser(token: String): User {
        val restTemplate = RestTemplateBuilder().build()
        val headers = HttpHeaders()
        headers.add("Authorization", "Bearer $token")
        val request = HttpEntity<Map<String, String>>(headers)
        val responseEntity = restTemplate.exchange(facebook_userinfo_url!!, HttpMethod.GET, request, String::class.java)
        println(responseEntity.body)
        if (responseEntity.statusCode == HttpStatus.OK) {
            val hashmap = objectMapper.readValue(responseEntity.body, HashMap::class.java)
            return userRepository.findByEmailAndSignupType(hashmap["email"].toString(), hashmap["signupType"] as SignupType)
                ?: userRepository.save(
                    User(email = hashmap["email"].toString(), profilePhotoURL = default_s3URL, signupType = SignupType.FACEBOOK)
                )
        } else throw AccessTokenException("Get user profile failed")
    }
}
