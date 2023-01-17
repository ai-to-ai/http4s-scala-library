import cats.effect.IO
import cats.effect.unsafe.implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    System.out.println("Here")
    System.out.println(ClientExample.getData().unsafeRunSync);
  }
}