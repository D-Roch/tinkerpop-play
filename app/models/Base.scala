package models

import com.tinkerpop.frames.{Property, VertexFrame, FramedGraph}
import com.tinkerpop.blueprints.TransactionalGraph
import scala.collection.JavaConversions._
import java.util.UUID
import scala.concurrent.{ExecutionContext, future, Future}
import play.api.libs.concurrent.Execution.Implicits._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import java.util.concurrent.Executors

/**
 * Base VertexFrame with meta getters / setters
 */
trait BaseVertexFrame extends VertexFrame {
  type Extracted <: BaseVertexFrame
  def meta: Base[Extracted]

  @Property("class_")
  def setCls(c: String)

  @Property("class_")
  def getCls: String

  @Property("uid")
  def setUID(uid: String)

  @Property("uid")
  def getUID: String

  @Property("name")
  def setName(name: String)

  @Property("name")
  def getName: String

  @Property("created_at")
  def setCreatedAt(dt: String)

  @Property("created_at")
  def getCreatedAt: String

  @Property("updated_at")
  def setUpdatedAt(dt: String)

  @Property("updated_at")
  def getUpdatedAt: String
}

trait DB[T <: BaseVertexFrame] {

  val CLASS_PROPERTY_NAME = "class_"

  val baseGraph = new OrientGraph("local:./tmp/orient", "admin", "admin")

  def graph = new FramedGraph(baseGraph)

  implicit def dbWrapper(vf: T) = new {

    val instance = ODatabaseRecordThreadLocal.INSTANCE.get
    System.out.println("1:" + Thread.currentThread().toString())

    implicit val ec = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor)

    /**
     * Saves pending changes to the Graph
     * @return
     */
    def save:Future[T] = {
      if(vf.getCreatedAt == null) vf.setCreatedAt(ISODateTimeFormat.dateTime().print(new DateTime))
      vf.setUpdatedAt(ISODateTimeFormat.dateTime().print(new DateTime))

      future {
        ODatabaseRecordThreadLocal.INSTANCE.set(instance)
        System.out.println("2:" + Thread.currentThread().toString())
        baseGraph.asInstanceOf[TransactionalGraph].commit
        vf.asInstanceOf[T]
      }
    }
    /**
     * Removes a Frame from the Graph
     */
    def delete:Future[Unit] = {
      graph.removeVertex(graph.getVertices("uid", vf.getUID).toList.head)

      future {
        ODatabaseRecordThreadLocal.INSTANCE.set(instance)
        System.out.println("2:" + Thread.currentThread().toString())
        baseGraph.asInstanceOf[TransactionalGraph].commit
      }
    }
  }

}

/**
 * Base provides some basic methods for instancing objects from the Graph Database
 * @tparam T
 */
abstract class Base[T <: BaseVertexFrame: Manifest] extends DB[T] { self =>

  def apply(): T = {
    val out:T = graph.frame(graph.addVertex(null), manifest[T].runtimeClass.asInstanceOf[Class[T]])
    out.setUID(UUID.randomUUID.toString)
    out.setCls(manifest[T].runtimeClass.getName)
    out.asInstanceOf[T]
  }

  /**
   * List frames from the graph corresponding to the extended model
   * @return
   */
  def list:Iterable[T] = {
    graph.frameVertices(graph.getVertices(CLASS_PROPERTY_NAME, manifest[T].runtimeClass.getName), manifest[T].runtimeClass.asInstanceOf[Class[T]])
  }
}
