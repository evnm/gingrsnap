package s3

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client

object S3 {
  val bucket = play.configuration("s3.bucket")

  val client = new AmazonS3Client(
    new BasicAWSCredentials(
      play.configuration("aws.access.key"),
      play.configuration("aws.secret.key")
    )
  )
}
