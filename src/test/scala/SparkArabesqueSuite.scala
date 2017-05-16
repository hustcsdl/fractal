package io.arabesque

import io.arabesque.computation._
import io.arabesque.conf.{Configuration, SparkConfiguration}
import io.arabesque.embedding._

import org.apache.spark.{SparkConf, SparkContext}

import org.scalatest.{BeforeAndAfterAll, FunSuite, Tag}

class SparkArabesqueSuite extends FunSuite with BeforeAndAfterAll {

  import SparkConfiguration._
  private val master = "local[2]"
  private val appName = "arabesque-spark"

  private var sampleGraphPath: String = _
  private var sc: SparkContext = _
  private var arab: ArabesqueContext = _
  private var arabGraph: ArabesqueGraph = _

  /** set up spark context */
  override def beforeAll: Unit = {
    // spark conf and context
    val conf = new SparkConf().
      setMaster(master).
      setAppName(appName)

    sc = new SparkContext(conf)
    arab = new ArabesqueContext(sc, "warn")

    val loader = classOf[SparkArabesqueSuite].getClassLoader
    val url = loader.getResource("sample.graph")
    sampleGraphPath = url.getPath
    arabGraph = arab.textFile (sampleGraphPath)
  }

  /** stop spark context */
  override def afterAll: Unit = {
    if (sc != null) {
      sc.stop()
      arab.stop()
    }
  }

  /** measuring elapsed time */
  def time[R](block: => R): R = {  
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println ("Elapsed time: " + ( (t1 - t0) * 10e-6 ) + " ms")
    result
  }

  /** expected results */

  val motifsOracle = Map(
    3 -> 24546
    )

  val cliquesOracle = Map(
    3 -> 1166
    )

  val cliquesPercOracle = Map(
    3 -> 234
    )

  val fsmOracle = Map(
    (100, 3) -> 31414
    )

  val trianglesOracle = scala.io.Source.fromFile (
    classOf[SparkArabesqueSuite].getClassLoader.
      getResource("sample-triangles.txt").getPath
    ).getLines.map (_ split "\\s+").
    map (a => (a(0).toInt, a(1).toInt)).toMap
  
  /** tests */

  test ("[motifs,filter]", Tag("motifs.filter")) { time {
    val motifsRes = arabGraph.motifs.explore(2)
    val filteredMotifsRes = motifsRes.filter (
      (e,c) => e.getVertices contains 3309
    )
    println (s"filtered motifs =\n" +
      s"${filteredMotifsRes.embeddings.collect.mkString("\n")}")
  }}

  test ("[triangles,filter]", Tag("triangles.filter")) { time {
    val trianglesRes = arabGraph.triangles.explore(3)
    val filteredTrianglesRes = trianglesRes.filter (
      (e,c) => e.getVertices contains 3309
    )
    println (s"filtered triangles =" +
      s" ${filteredTrianglesRes.embeddings.collect.mkString("\n")}")
  }}
  
  test ("[triangles,expand,filter]",
      Tag("triangles.expand.filter")) { time {
    val trianglesRes = arabGraph.triangles.explore(3).expand.explore(2)
    val filteredTrianglesRes = trianglesRes.filter (
      (e,c) => e.getVertices contains 3309
    )
    println (s"filtered triangles =" +
      s" ${filteredTrianglesRes.embeddings.collect.mkString("\n")}")
  }}

  test ("[fsm,motifs,cliques,concurrent]",
      Tag("fsm.motifs.cliques.concurrent")) { time {
    import scala.concurrent._
    import scala.concurrent.duration.Duration
    import ExecutionContext.Implicits.global

    val motifsFuture = Future {
      val motifsRes = arabGraph.motifs(3).exploreAll()
      val motifsEmbeddings = motifsRes.embeddings
      assert (motifsEmbeddings.count == motifsOracle(3))
      assert (motifsEmbeddings.distinct.count == motifsOracle(3))
    }

    val fsmFuture = Future {
      val fsmRes = arabGraph.fsm(100, 3).exploreAll()
      val fsmEmbeddings = fsmRes.embeddings
      assert (fsmEmbeddings.count == fsmOracle((100, 3)))
      assert (fsmEmbeddings.distinct.count == fsmOracle((100, 3)))
    }

    val cliquesFromMotifsFuture = Future {
      val motifsRes = arabGraph.motifs.explore(2)
      val cliquesRes = motifsRes.cliques.explore(3)
      val trianglesRes = motifsRes.triangles.explore(1)
      assert (cliquesRes.embeddings.count <= cliquesOracle(3))
      assert (trianglesRes.embeddings.count == cliquesOracle(3))
      assert (arabGraph.cliques(3).embeddings.count == cliquesOracle(3))

      val backMotifsRes1 = cliquesRes.explore(-3)
      val backMotifsRes2 = trianglesRes.explore(-1)
      val backMotifsRes1Count = backMotifsRes1.embeddings.distinct.count
      val backMotifsRes2Count = backMotifsRes2.embeddings.distinct.count
      assert (backMotifsRes1Count == backMotifsRes2Count)
      assert (backMotifsRes2Count == motifsRes.embeddings.count)
    }

    Await.result(motifsFuture, Duration.Inf)
    Await.result(fsmFuture, Duration.Inf)
    Await.result(cliquesFromMotifsFuture, Duration.Inf)
  }}
  
  test ("[motifs,odag]", Tag("motifs.odag")) { time {
    val motifsRes = arabGraph.motifs (3).
      set ("comm_strategy", COMM_ODAG_SP)
    val embeddings = motifsRes.embeddings
    assert (embeddings.count == motifsOracle(3))
    assert (embeddings.distinct.count == motifsOracle(3))
  }}

  test ("[motifs,embedding]", Tag("motifs.embedding")) { time {
    val motifsRes = arabGraph.motifs (3).
      set ("comm_strategy", COMM_EMBEDDING).
      set ("num_partitions", 1)
    val embeddings = motifsRes.embeddings
    assert (embeddings.count == motifsOracle(3))
    assert (embeddings.distinct.count == motifsOracle(3))
  }}

  test ("[fsm,odag]", Tag("fsm.odag")) { time {
    val fsmRes = arabGraph.fsm (100, 3).
      set ("comm_strategy", COMM_ODAG_SP)
    val embeddings = fsmRes.embeddings
    assert (embeddings.count == fsmOracle((100, 3)))
    assert (embeddings.distinct.count == fsmOracle((100, 3)))
  }}
  
  test ("[fsm,embedding]", Tag("fsm.embedding")) { time {
    val fsmRes = arabGraph.fsm (100, 3).
      set ("comm_strategy", COMM_EMBEDDING)
    val embeddings = fsmRes.embeddings
    assert (embeddings.count == fsmOracle((100, 3)))
    assert (embeddings.distinct.count == fsmOracle((100, 3)))
  }}

  test ("[triangles,odag]", Tag("triangles.odag")) { time {
    import org.apache.hadoop.io.{IntWritable, LongWritable}

    val trianglesRes = arabGraph.allStepsTriangles.
      set ("comm_strategy", COMM_ODAG_SP)

    val triangleMemberships = trianglesRes.
      aggregation [IntWritable,LongWritable] ("membership")

    triangleMemberships.foreach { case (v,n) =>
      assert (trianglesOracle(v.get) == n.get)
    }

  }}

  test ("[triangles,embedding]", Tag("triangles.embedding")) { time {
    import org.apache.hadoop.io.{IntWritable, LongWritable}

    val trianglesRes = arabGraph.triangles.
      set ("comm_strategy", COMM_EMBEDDING)

    val triangleMemberships = trianglesRes.
      aggregation [IntWritable,LongWritable] ("membership")

    triangleMemberships.foreach { case (v,n) =>
      assert (trianglesOracle(v.get) == n.get)
    }
  }}

  test ("[cliques,odag]", Tag("cliques.odag")) { time {
    val cliquesRes = arabGraph.cliques (3).
      set ("comm_strategy", COMM_ODAG_SP)
    val embeddings = cliquesRes.embeddings
    assert (embeddings.count == cliquesOracle(3))
    assert (embeddings.distinct.count == cliquesOracle(3))
  }}

  test ("[cliques,embedding]", Tag("cliques.embedding")) { time {
    val cliquesRes = arabGraph.cliques (3).
      set ("comm_strategy", COMM_EMBEDDING)
    val embeddings = cliquesRes.embeddings
    assert (embeddings.count == cliquesOracle(3))
    assert (embeddings.distinct.count == cliquesOracle(3))
  }}

  test ("[cliques percolation]", Tag("cliques.percolation")) { time {
    import io.arabesque.utils.collection.{IntArrayList, UnionFindOps}
    import scala.collection.JavaConverters._
    import org.apache.hadoop.io._

    val maxsize = 3
    val cliquepercRes = arabGraph.cliquesPercolation (maxsize)
    val cliqueAdjacencies = cliquepercRes.
      aggregationStorage [IntArrayList,IntArrayList] ("membership")
    val cliqueAdjacenciesBc = sc.broadcast (cliqueAdjacencies)
    val cliques = cliquepercRes.
      aggregationRDD [IntArrayList,VertexInducedEmbedding] ("cliques")

    val communities = cliques.map { case (repr,e) =>
      val m = cliqueAdjacenciesBc.value
      val key = UnionFindOps.find [IntArrayList] (
        v => m.getValue(v),
        (k,v) => m.aggregateWithReusables (k, v),
        repr.value
      )
      (key, e.value)
    }.reduceByKey { (e1,e2) =>
      e2.getVertices.iterator.asScala.
        foreach (v => if (!(e1.getVertices contains v)) e1.addWord (v))
      e1
    }

    assert (communities.count == cliquesPercOracle(3))
  }}
}
