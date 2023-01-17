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
  case class TokenRequest(ChallengeName: String, ChallengeResponses: ChallengeResponses, ClientId: String, ClientMetadata: ClientMetadata, Session: String)
  case class AuthenticationResult(AccessToken: String, ExpiresIn: Int, IdToken: String, RefreshToken: String, TokenType: String)
  case class TokenResponse(AuthenticationResult: AuthenticationResult)

  case class Token(IdToken: String, ExpiresIn: Int)

    def interceptor(f: Int => String): Int => String = in => {
    val result = f(in + 1)
      s"${f(in)}, $result"
    }

    val f1: Int => String = _.toString

    val wrapper: Int => String = interceptor(f1)

  def getAuthResponse(client: Client[IO], authRequest: Entity[IO]): IO[String] = {
    for {
      response <- client
        .expect(Request[IO](
          Method.POST,
          uri"https://cognito-idp.us-east-1.amazonaws.com",
          body = authRequest.body,
          headers = Headers(("X-Amz-Target","AWSCognitoIdentityProviderService.InitiateAuth"),("Content-Type","application/x-amz-json-1.1"))
        ))(jsonOf[IO, AuthResponse])
    } yield response.Session
  }

  def getTokenResponse(client: Client[IO], tokenRequest: Entity[IO]): IO[AuthenticationResult] = {
    for {
      response <- client
        .expect(Request[IO](
          Method.POST,
          uri"https://cognito-idp.us-east-1.amazonaws.com",
          body = tokenRequest.body,
          headers = Headers(("X-Amz-Target","AWSCognitoIdentityProviderService.RespondToAuthChallenge"),("Content-Type","application/x-amz-json-1.1"))
        ))(jsonOf[IO, TokenResponse])
    } yield response.AuthenticationResult
  }

  // def run(args: List[String]): IO[ExitCode] =ExitCode.Success

  def getData(): IO[AuthenticationResult] =
    BlazeClientBuilder[IO].resource.use{
      client => for {
        authResponse <- getAuthResponse(client, jsonEncoderOf[IO, AuthRequest].toEntity(AuthRequest(AuthParameters("recognitosamplejava-6286e09dbb07d2d2cab2c1c9"),"CUSTOM_AUTH","538gsfq2es360re9bldpcspm57")))
        token <- getTokenResponse(client, jsonEncoderOf[IO, TokenRequest].toEntity(TokenRequest("CUSTOM_CHALLENGE",ChallengeResponses("xjvTpWxVc2xTzGkf","recognitosamplejava-6286e09dbb07d2d2cab2c1c9"),"538gsfq2es360re9bldpcspm57",ClientMetadata("6286e09dbb07d2d2cab2c1c9"),authResponse)))
        _ <- IO.println(token)
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

