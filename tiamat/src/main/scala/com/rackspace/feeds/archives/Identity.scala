package com.rackspace.feeds.archives

import java.nio.charset.Charset

import org.apache.http.HttpHost
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.{BasicCredentialsProvider, BasicAuthCache, HttpClientBuilder}
import org.codehaus.jackson.map.ObjectMapper
import org.slf4j.LoggerFactory

import scala.io.Source

import Errors._

/**
 * Groups methods related to interacting with Identity API.
 */
object Identity {

  val ACCEPT = "Accept"
  val TOKEN = "X-Auth-Token"
  val APP_JSON = "application/json"
  val APP_XML = "application/atom+xml"
  val CONTENT_TYPE = "Content-Type"
}

class Identity( host : String, admin : String, apiKey : String, pw : String )  extends Serializable {

  val identity = s"https://${host}"
  val logger = LoggerFactory.getLogger(getClass)

  import Identity._

  /**
   * Get impersonation token for given user.
   *
   * @param user
   * @param token
   * @return
   */
  def impersonate(user: String, token: String): String = {

    logger.debug(s"Impersonating user:[$user]")
    val client = HttpClientBuilder.create.build
    val post = new HttpPost(s"${identity}/v2.0/RAX-AUTH/impersonation-tokens")
    post.addHeader(ACCEPT, APP_JSON)
    post.addHeader(CONTENT_TYPE, APP_JSON)
    post.addHeader(TOKEN, token)
    post.setEntity(new StringEntity( s"""{"RAX-AUTH:impersonation" : {"user" : {"username" : "$user"}, "expire-in-seconds":10800}}""",
      Charset.forName("UTF-8")))

    val resp = client.execute(post)

    val output = Source.fromInputStream(resp.getEntity.getContent).mkString

    resp.getStatusLine.getStatusCode match {

      case 200 => {
        logger.debug(s"Successfully impersonated user:[$user]")
        val body = (new ObjectMapper).readTree(output)
        body.get("access").get("token").get("id").getTextValue
      }
      case _ => {
        val impersonateErrorMsg: String = IMPERSONATE(user, Source.fromInputStream(resp.getEntity.getContent).mkString)
        logger.error(s"Error impersonatiing user:[$user], status code:[${resp.getStatusLine.getStatusCode}], message:[$impersonateErrorMsg]")
        throw new RestException(resp.getStatusLine.getStatusCode,
          impersonateErrorMsg)
      }
    }
  }


  /**
   * Get token for given user.
   *
   * @return
   */
  def getToken(): String = {

    logger.debug("Retrieving admin token")
    val client = HttpClientBuilder.create.build
    val post = new HttpPost(s"${identity}/v2.0/tokens")
    post.addHeader(ACCEPT, APP_JSON)
    post.addHeader(CONTENT_TYPE, APP_JSON)
    post.setEntity(new StringEntity( s"""{ "auth":{ "RAX-KSKEY:apiKeyCredentials":{ "username": "$admin", "apiKey":"$apiKey"} } }""",
      Charset.forName("UTF-8")))

    val resp = client.execute(post)

    val output = Source.fromInputStream(resp.getEntity.getContent).mkString

    resp.getStatusLine.getStatusCode match {

      case 200 => {

        logger.debug("Successfully retrieved admin token")
        val body = (new ObjectMapper).readTree(output)
        body.get("access").get("token").get("id").getTextValue
      }
      case _ => {

        val adminTokenErrorMsg: String = ADMIN_TOKEN(output)
        logger.error(s"Error getting token for admin: status code:[${resp.getStatusLine.getStatusCode}], message:[$adminTokenErrorMsg]")
        throw new RestException(resp.getStatusLine.getStatusCode,
          adminTokenErrorMsg)
      }
    }
  }


  /**
   * Get admin user for given tenant.
   *
   *
   * @param tenant

   * @return
   */
  def getTenantAdmin(tenant: String): String = {

    logger.debug(s"Retrieving admin for tenant:[$tenant] ")
    val targetHost = new HttpHost(host, 443, "https")
    val authCache = new BasicAuthCache
    authCache.put(targetHost, new BasicScheme)

    val credsProvider = new BasicCredentialsProvider
    credsProvider.setCredentials(AuthScope.ANY,
      new UsernamePasswordCredentials(admin, pw))

    val httpContext = HttpClientContext.create
    httpContext.setCredentialsProvider(credsProvider)
    httpContext.setAuthCache(authCache)

    val requestBuilder = RequestConfig.custom
    requestBuilder.setAuthenticationEnabled(true)

    val builder = HttpClientBuilder.create.setDefaultRequestConfig(requestBuilder.build).setDefaultCredentialsProvider(credsProvider)
    val client = builder.build

    val get = new HttpGet(s"${identity}/v1.1/mosso/$tenant")
    get.addHeader(ACCEPT, APP_JSON)

    val resp = client.execute(get, httpContext)
    val output = Source.fromInputStream(resp.getEntity.getContent).mkString

    resp.getStatusLine.getStatusCode match {

      case 200 => {
        logger.debug(s"Successfully retrieved admin for tenant:[$tenant] ")
        val body = (new ObjectMapper).readTree(output)
        body.get("user").get("id").getTextValue
      }
      case _ => {
        val adminUserErrorMsg: String = ADMIN_USER(tenant, output)
        logger.error(s"Error getting admin for tenant:[$tenant]: status code:[${resp.getStatusLine.getStatusCode}], message:[$adminUserErrorMsg]")
        throw new RestException(resp.getStatusLine.getStatusCode,
          adminUserErrorMsg )
      }
    }
  }
}


