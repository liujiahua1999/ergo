package org.ergoplatform.db

import org.iq80.leveldb.DB

/**
  * A LevelDB wrapper providing a convenient db interface.
  */
final class LDBKVStore(protected val db: DB) extends KVStore {

  def update(toInsert: Seq[(K, V)], toRemove: Seq[K]): Unit = {
    val batch = db.createWriteBatch()
    try {
      toInsert.foreach { case (k, v) => batch.put(k.toArray, v.toArray) }
      toRemove.foreach(x => batch.delete(x.toArray))
      db.write(batch)
    } finally {
      batch.close()
    }
  }

  def put(values: (K, V)*): Unit = update(values, Seq.empty)

  def delete(keys: K*): Unit = update(Seq.empty, keys)

}
