package fi.vm.sade.valintatulosservice.json

import java.io.PrintWriter

import org.json4s.Formats

object JsonStreamWriter {
  val writeSize = 100
  def writeJsonStream(objects: Iterator[AnyRef], writer: PrintWriter, render: AnyRef => String): Unit = {
    writer.print("[")
    try {
      objects.zipWithIndex.grouped(writeSize).foreach(_.foreach { case (item, index) =>
        if (index > 0) {
          writer.print(",")
        }
        writer.print(render(item))
      })
      writer.print("]")
    } catch {
      case t: Throwable => throw new StreamingFailureException(t, s""", {"error": "${t.getMessage}"}] """)
    }
  }

  def writeJsonStream(objects: Iterator[AnyRef], writer: PrintWriter)(implicit formats: Formats): Unit = {
    writeJsonStream(objects, writer, o => org.json4s.jackson.Serialization.write(o))
  }
}

class StreamingFailureException(cause: Throwable, val contentToInsertToBody: String) extends RuntimeException(cause)
