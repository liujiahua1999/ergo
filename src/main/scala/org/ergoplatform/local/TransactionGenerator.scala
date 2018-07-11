package org.ergoplatform.local

import akka.actor.{Actor, ActorRef, ActorRefFactory, Cancellable, Props}
import org.ergoplatform.ErgoBoxCandidate
import org.ergoplatform.local.TransactionGenerator.{FetchBoxes, StartGeneration, StopGeneration}
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.nodeView.history.ErgoHistory
import org.ergoplatform.nodeView.mempool.ErgoMemPool
import org.ergoplatform.nodeView.state.UtxoState
import org.ergoplatform.nodeView.wallet.ErgoWallet
import org.ergoplatform.nodeView.wallet.ErgoWalletActor.GenerateTransaction
import org.ergoplatform.settings.TestingSettings
import scorex.core.NodeViewHolder.ReceivableMessages.{GetDataFromCurrentView, LocallyGeneratedTransaction}
import scorex.core.utils.ScorexLogging
import sigmastate.Values

import scala.concurrent.duration._


class TransactionGenerator(viewHolder: ActorRef,
                           ergoWalletActor: ActorRef,
                           settings: TestingSettings) extends Actor with ScorexLogging {
  var txGenerator: Cancellable = _

  var isStarted = false

  var currentFullHeight = 0

  @SuppressWarnings(Array("TraversableHead"))
  override def receive: Receive = {
    case StartGeneration =>
      if (!isStarted) {
        context.system.scheduler.schedule(1500.millis, 1500.millis)(self ! FetchBoxes)(context.system.dispatcher)
      }

    case FetchBoxes =>
      viewHolder ! GetDataFromCurrentView[ErgoHistory, UtxoState, ErgoWallet, ErgoMemPool, Unit] { v =>
        val fbh = v.history.fullBlockHeight
        if(fbh > currentFullHeight){
          currentFullHeight = fbh

          //todo: real prop
          ergoWalletActor ! GenerateTransaction(Seq(new ErgoBoxCandidate(1, Values.TrueLeaf)))
        }
      }

    case txOpt: Option[ErgoTransaction]@unchecked =>
      txOpt.foreach { tx =>
        viewHolder ! LocallyGeneratedTransaction[ErgoTransaction](tx)
      }

    case StopGeneration =>
      txGenerator.cancel()
  }
}

object TransactionGenerator {

  case object StartGeneration

  case object FetchBoxes

  case object StopGeneration
}

object TransactionGeneratorRef {
  def props(viewHolder: ActorRef, ergoWalletActor: ActorRef, settings: TestingSettings): Props =
    Props(new TransactionGenerator(viewHolder, ergoWalletActor, settings))

  def apply(viewHolder: ActorRef, ergoWalletActor: ActorRef, settings: TestingSettings)
           (implicit context: ActorRefFactory): ActorRef =
    context.actorOf(props(viewHolder, ergoWalletActor, settings))

  def apply(viewHolder: ActorRef, ergoWalletActor: ActorRef, settings: TestingSettings, name: String)
           (implicit context: ActorRefFactory): ActorRef =
    context.actorOf(props(viewHolder, ergoWalletActor, settings), name)
}
