package scalaz
package std

import annotation.tailrec
import collection.IndexedSeqLike
import collection.generic.{CanBuildFrom, GenericTraversableTemplate}

trait IndexedSeqInstances0 {
  implicit def indexedSeqEqual[A](implicit A0: Equal[A]) = new IndexedSeqEqual[A] {
    implicit def A = A0
  }
}

trait IndexedSeqInstances extends IndexedSeqInstances0 {
  implicit val indexedSeqInstance = new Traverse[IndexedSeq] with MonadPlus[IndexedSeq] with Each[IndexedSeq] with Index[IndexedSeq] with Length[IndexedSeq] with ApplicativePlus[IndexedSeq] with Zip[IndexedSeq] with Unzip[IndexedSeq] {
    def each[A](fa: IndexedSeq[A])(f: (A) => Unit) = fa foreach f
    def index[A](fa: IndexedSeq[A], i: Int) = if (fa.size > i) Some(fa(i)) else None
    def length[A](fa: IndexedSeq[A]) = fa.length
    def point[A](a: => A) = scala.IndexedSeq(a)
    def bind[A, B](fa: IndexedSeq[A])(f: A => IndexedSeq[B]) = fa flatMap f
    def empty[A] = scala.IndexedSeq()
    def plus[A](a: IndexedSeq[A], b: => IndexedSeq[A]) = a ++ b
    override def map[A, B](v: IndexedSeq[A])(f: A => B) = v map f

    def zip[A, B](a: => IndexedSeq[A], b: => IndexedSeq[B]) = a zip b
    def unzip[A, B](a: IndexedSeq[(A, B)]) = a.unzip

    def traverseImpl[F[_], A, B](v: IndexedSeq[A])(f: A => F[B])(implicit F: Applicative[F]) = {
      DList.fromList(v.toList).foldr(F.point(IndexedSeq[B]())) {
         (a, fbs) => F.map2(f(a), fbs)(_ +: _)
      }
    }
    
    override def traverseS[S,A,B](v: IndexedSeq[A])(f: A => State[S,B]): State[S,IndexedSeq[B]] =
      State((s: S) => 
        v.foldLeft((s, IndexedSeq[B]()))((acc, a) => {
          val bs = f(a)(acc._1)
          (bs._1, acc._2 :+ bs._2)
        }))

    override def foldRight[A, B](fa: IndexedSeq[A], z: => B)(f: (A, => B) => B) = {
      import scala.collection.mutable.ArrayStack
      val s = new ArrayStack[A]
      fa.foreach(a => s += a)
      var r = z
      while (!s.isEmpty) {
        // force and copy the value of r to ensure correctness
        val w = r
        r = f(s.pop, w)
      }
      r
    }

  }

  implicit def indexedSeqMonoid[A]: Monoid[IndexedSeq[A]] = new Monoid[IndexedSeq[A]] {
    def append(f1: IndexedSeq[A], f2: => IndexedSeq[A]) = f1 ++ f2
    def zero: IndexedSeq[A] = IndexedSeq()
  }

  implicit def indexedSeqShow[A: Show]: Show[IndexedSeq[A]] = new Show[IndexedSeq[A]] {
    def show(as: IndexedSeq[A]) =
      (List('[') +: (indexedSeq.intersperse(as.map(Show[A].show(_)), List(',')) :+ List(']'))).flatten.toList
  }

  implicit def indexedSeqOrder[A](implicit A0: Order[A]): Order[IndexedSeq[A]] = new IndexedSeqOrder[A] {
    implicit def A = A0
  }

}

trait IndexedSeqSubFunctions {
  type IxSq[+A] <: IndexedSeq[A] with GenericTraversableTemplate[A, IxSq] with IndexedSeqLike[A, IxSq[A]]
  protected implicit def buildIxSq[A, B]: CanBuildFrom[IxSq[A], B, IxSq[B]]
  protected def monad: Monad[IxSq]
  protected def empty[A]: IxSq[A]

  /** Intersperse the element `a` between each adjacent pair of elements in `as` */
  final def intersperse[A](as: IxSq[A], a: A): IxSq[A] = {
    @tailrec
    def intersperse0(accum: IxSq[A], rest: IxSq[A]): IxSq[A] =
      if (rest.isEmpty) accum else if (rest.tail.isEmpty) rest.head +: accum else intersperse0(a +: rest.head +: accum, rest.tail)
    intersperse0(empty, as).reverse
  }

  final def intercalate[A](as1: IxSq[IxSq[A]], as2: IxSq[A]): IxSq[A] = intersperse(as1, as2).flatten

  final def toNel[A](as: IxSq[A]): Option[NonEmptyList[A]] = 
    if (as.isEmpty) None else Some(NonEmptyList.nel(as.head, as.tail.toList))

  final def toZipper[A](as: IxSq[A]): Option[Zipper[A]] =
    stream.toZipper(as.toStream)

  final def zipperEnd[A](as: IxSq[A]): Option[Zipper[A]] =
    stream.zipperEnd(as.toStream)

  /**
   * Returns `f` applied to the contents of `as` if non-empty, otherwise, the zero element of the `Monoid` for the type `B`.
   */
  final def <^>[A, B: Monoid](as: IxSq[A])(f: NonEmptyList[A] => B): B =
    if (as.isEmpty) Monoid[B].zero else f(NonEmptyList.nel(as.head, as.tail.toList))

  final def takeWhileM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[IxSq[A]] =
    if (as.isEmpty) Monad[M].point(empty) else Monad[M].bind(p(as.head))(b =>
      if (b) Monad[M].map(takeWhileM(as.tail)(p))((tt: IxSq[A]) => as.head +: tt) else Monad[M].point(empty))

  final def takeUntilM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[IxSq[A]] =
    takeWhileM(as)((a: A) => Monad[M].map(p(a))((b) => !b))

  final def filterM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[IxSq[A]] =
    if (as.isEmpty) Monad[M].point(empty) else {
      def g = filterM(as.tail)(p)
      Monad[M].bind(p(as.head))(b => if (b) Monad[M].map(g)(tt => as.head +: tt) else g)
    }
  
  final def findM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[Option[A]] =
    if (as.isEmpty) Monad[M].point(None: Option[A]) else Monad[M].bind(p(as.head))(b =>
      if (b) Monad[M].point(Some(as.head): Option[A]) else findM(as.tail)(p))

  final def powerset[A](as: IxSq[A]): IxSq[IxSq[A]] = {
    implicit val indexedSeqInstance = monad
    val tf = empty[Boolean] :+ true :+ false
    filterM(as)(_ => tf)
  }

  final def partitionM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[(IxSq[A], IxSq[A])] =
    if (as.isEmpty) Monad[M].point(empty[A], empty[A]) else
      Monad[M].bind(p(as.head))(b =>
        Monad[M].map(partitionM(as.tail)(p)) {
          case (x, y) => if (b) (as.head +: x, y) else (x, as.head +: y)
        }
      )

  final def spanM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[(IxSq[A], IxSq[A])] =
    if (as.isEmpty) Monad[M].point(empty, empty) else
      Monad[M].bind(p(as.head))(b =>
        if (b) Monad[M].map(spanM(as.tail)(p))((k: (IxSq[A], IxSq[A])) => (as.head +: k._1, k._2))
        else Monad[M].point(empty, as))

  final def breakM[A, M[_] : Monad](as: IxSq[A])(p: A => M[Boolean]): M[(IxSq[A], IxSq[A])] =
    spanM(as)(a => Monad[M].map(p(a))((b: Boolean) => !b))

  final def groupByM[A, M[_] : Monad](as: IxSq[A])(p: (A, A) => M[Boolean]): M[IxSq[IxSq[A]]] =
    if (as.isEmpty) Monad[M].point(empty) else
      Monad[M].bind(spanM(as.tail)(p(as.head, _))) {
        case (x, y) =>
          Monad[M].map(groupByM(y)(p))((g: IxSq[IxSq[A]]) => (as.head +: x) +: g)
      }

  final def mapAccumLeft[A, B, C](as: IxSq[A])(c: C, f: (C, A) => (C, B)): (C, IxSq[B]) =
    if (as.isEmpty) (c, empty) else {
      val (i, j) = f(c, as.head)
      val (k, v) = mapAccumLeft(as.tail)(i, f)
      (k, j +: v)
    }

  final def mapAccumRight[A, B, C](as: IxSq[A])(c: C, f: (C, A) => (C, B)): (C, IxSq[B]) =
    if (as.isEmpty) (c, empty) else {
      val (i, j) = mapAccumRight(as.tail)(c, f)
      val (k, v) = f(i, as.head)
      (k, v +: j)
    }

  final def tailz[A](as: IxSq[A]): IxSq[IxSq[A]] =
    if (as.isEmpty) empty[A] +: empty else as +: tailz(as.tail)

  final def initz[A](as: IxSq[A]): IxSq[IxSq[A]] =
    empty +: (if (as.isEmpty) empty else (initz(as.tail) map (as.head +: _)))

  final def allPairs[A](as: IxSq[A]): IxSq[(A, A)] =
    tailz(as).tail flatMap (as zip _)

  final def adjacentPairs[A](as: IxSq[A]): IxSq[(A, A)] =
    if (as.isEmpty) empty else as zip as.tail
}

trait IndexedSeqFunctions extends IndexedSeqSubFunctions {
  type IxSq[+A] = IndexedSeq[A]
  protected def buildIxSq[A, B] = implicitly
  protected def monad = indexedSeq.indexedSeqInstance
  protected def empty[A] = IndexedSeq()
}

object indexedSeq extends IndexedSeqInstances with IndexedSeqFunctions {
  object indexedSeqSyntax extends scalaz.syntax.std.ToIndexedSeqOps
}

trait IndexedSeqEqual[A] extends Equal[IndexedSeq[A]] {
  implicit def A: Equal[A]

  override def equalIsNatural: Boolean = A.equalIsNatural

  override def equal(a1: IndexedSeq[A], a2: IndexedSeq[A]) = (a1 corresponds a2)(Equal[A].equal)
}

trait IndexedSeqOrder[A] extends Order[IndexedSeq[A]] with IndexedSeqEqual[A] {
  implicit def A: Order[A]

  import Ordering._

  def order(a1: IndexedSeq[A], a2: IndexedSeq[A]) =
    (a1, a2) match {
      case (IndexedSeq(), IndexedSeq()) => EQ
      case (IndexedSeq(), y)        => LT
      case (x, IndexedSeq())        => GT
      case (as, bs) => Order[A].order(as.head, bs.head) match {
        case EQ => order(as.tail, bs.tail)
        case x  => x
      }
    }

}
