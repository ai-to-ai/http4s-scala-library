import cats.effect._
import cats.instances.map
import io.circe.generic.auto._
import org.http4s.Status.Successful
import org.http4s.{Entity, Header, Headers, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.syntax.all._
import org.http4s.HttpRoutes


object ClientExample {
  case class AuthParameters(USERNAME: String)
  case class AuthRequest(AuthParameters: AuthParameters, AuthFlow: String, ClientId: String)
  case class AuthResponse(Session: String)
  case class ChallengeResponses(ANSWER: String, USERNAME: String)
  case class ClientMetadata(IssuedForService: String)
  case class TokenRequest(ChallengeName: String, ChallengeResponses: ChallengeResponses, ClientId: String, ClientMetadata: ClientMetadata, Session: Option[String])
  case class AuthenticationResult(AccessToken: String, ExpiresIn: Int, IdToken: String, RefreshToken: String, TokenType: String)
  case class TokenResponse(AuthenticationResult: AuthenticationResult)

  case class Token(IdToken: String, ExpiresIn: Int)

  val cognitoUrl = uri"https://cognito-idp.us-east-1.amazonaws.com"
  
  val clientId = "538gsfq2es360re9bldpcspm57"
  val username = "recognitosamplejava-6286e09dbb07d2d2cab2c1c9"
  val authflow = "CUSTOM_AUTH"
  val challengeName = "CUSTOM_CHALLENGE"
  val challengeAnswer = "xjvTpWxVc2xTzGkf"
  val issuedForService = "6286e09dbb07d2d2cab2c1c9"
  val authResponseHeader = Headers(
                            ("X-Amz-Target","AWSCognitoIdentityProviderService.InitiateAuth"),
                            ("Content-Type","application/x-amz-json-1.1")
                          )
  val tokenResponseHeader = Headers(
                            ("X-Amz-Target","AWSCognitoIdentityProviderService.RespondToAuthChallenge"),
                            ("Content-Type","application/x-amz-json-1.1")
                          )
  val authRequest = AuthRequest(
                      AuthParameters(username),
                      authflow,
                      clientId
                    )
  val tokenRequest = TokenRequest(
                      challengeName,
                      ChallengeResponses(
                        challengeAnswer,
                        username
                      ),
                      clientId,
                      ClientMetadata(issuedForService),
                      None
                    )

  def getAuthResponse(client: Client[IO], authRequest: Entity[IO]): IO[String] = {
    for {
      response <- client
        .expect(Request[IO](
          Method.POST,
          cognitoUrl,
          body = authRequest.body,
          headers = authResponseHeader
        ))(jsonOf[IO, AuthResponse])
    } yield response.Session
  }

  def getTokenResponse(client: Client[IO], tokenRequest: Entity[IO]): IO[AuthenticationResult] = {
    for {
      response <- client
        .expect(Request[IO](
          Method.POST,
          cognitoUrl,
          body = tokenRequest.body,
          headers = tokenResponseHeader
        ))(jsonOf[IO, TokenResponse])
    } yield response.AuthenticationResult
  }

  def getData(): IO[AuthenticationResult] =
    BlazeClientBuilder[IO].resource.use{
      client => for {
        authResponse <- getAuthResponse(client, jsonEncoderOf[IO, AuthRequest].toEntity(authRequest))    
        token <- getTokenResponse(client, jsonEncoderOf[IO, TokenRequest].toEntity(tokenRequest.copy(Session = Some(authResponse))))
      } yield (token)
    }
  
   def addHeader(resp: Response[IO], header: Header.ToRaw) =
    resp match {
      case Status.Successful(resp) => resp.putHeaders(header)
      case resp => resp
    }

  def apply(service: HttpRoutes[IO], header: Header.ToRaw) =
    service.map(addHeader(_, header))

}

