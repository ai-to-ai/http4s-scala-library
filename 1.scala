package com.disneystreaming.recognito

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.instances.map
import com.guizmaii.scalajwt.implementations.{AwsCognitoJwtValidator, CognitoUserPoolId, S3Region}
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.nimbusds.jwt.SignedJWT
import io.circe.generic.auto._
import io.circe.parser.parse
import org.http4s.Status.Successful
import org.http4s.{AuthedRequest, Entity, Header, Headers, HttpRoutes, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.syntax.all._


object Client extends IOApp {
  case class AuthParameters(USERNAME: String)
  case class AuthRequest(AuthParameters: AuthParameters, AuthFlow: String, ClientId: String)
  case class AuthResponse(Session: String)
  case class ChallengeResponses(ANSWER: String, USERNAME: String)
  case class ClientMetadata(IssuedForService: String)
  case class TokenRequest(ChallengeName: String, ChallengeResponses: ChallengeResponses, ClientId: String, ClientMetadata: ClientMetadata, Session: String)
  case class AuthenticationResult(AccessToken: String, ExpiresIn: Int, IdToken: String, RefreshToken: String, TokenType: String)
  case class TokenResponse(AuthenticationResult: AuthenticationResult)

  case class Token(IdToken: String, ExpiresIn: Int)


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


  def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO].resource.use{
      client => for {
        authResponse <- getAuthResponse(client, jsonEncoderOf[IO, AuthRequest].toEntity(AuthRequest(AuthParameters(""),"CUSTOM_AUTH","")))
        token <- getTokenResponse(client, jsonEncoderOf[IO, TokenRequest].toEntity(TokenRequest("CUSTOM_CHALLENGE",ChallengeResponses("",""),"",ClientMetadata(""),authResponse)))
        _ <- IO.println(token.IdToken)
      } yield (ExitCode.Success)
    }


}
