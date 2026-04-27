package fi.vm.sade.valintatulosservice

import org.specs2.mutable.Specification

import java.time.LocalDateTime

class json4sCustomFormatsSpec extends Specification with json4sCustomFormats {

  "parseDateTime" should {
    "parse strings like 2014-08-26T16:05:23.943Z" in {
      val result = parseDateTime("2014-08-26T16:05:23.943Z")

      result.toLocalDateTime mustEqual LocalDateTime.of(2014, 8, 26, 16, 5, 23, 943000000)
      result.getOffset.getTotalSeconds mustEqual 0
    }

    "parse strings like 2016-10-12T04:11:19.328+0000" in {
      val result = parseDateTime("2016-10-12T04:11:19.328+0000")

      result.toLocalDateTime mustEqual LocalDateTime.of(2016, 10, 12, 4, 11, 19, 328000000)
      result.getOffset.getTotalSeconds mustEqual 0
    }

    "parse strings like 2026-02-18T07:39:11Z" in {
      val result = parseDateTime("2026-02-18T07:39:11Z")

      result.toLocalDateTime mustEqual LocalDateTime.of(2026, 2, 18, 7, 39, 11, 0)
      result.getOffset.getTotalSeconds mustEqual 0
    }

    "parse strings like 2026-02-18T07:39:11+0000" in {
      val result = parseDateTime("2026-02-18T07:39:11+0000")

      result.toLocalDateTime mustEqual LocalDateTime.of(2026, 2, 18, 7, 39, 11, 0)
      result.getOffset.getTotalSeconds mustEqual 0
    }
  }
}
