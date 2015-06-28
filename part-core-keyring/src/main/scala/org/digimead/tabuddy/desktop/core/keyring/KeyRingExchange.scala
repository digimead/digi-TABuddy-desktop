/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014-2015 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.core.keyring

import java.io.{ BufferedInputStream, InputStream, OutputStream }
import org.bouncycastle.bcpg.{ ArmoredOutputStream, BCPGInputStream, CompressionAlgorithmTags }
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.{ PGPCompressedData, PGPCompressedDataGenerator, PGPObjectFactory, PGPPublicKey, PGPPublicKeyRing, PGPSecretKeyRing, PGPUtil }
import scala.language.reflectiveCalls

/**
 * Key exchange part for keyring implementation.
 */
trait KeyRingExchange {
  /** Export PGP public key and close the output stream. */
  def export(publicKey: PGPPublicKey, os: OutputStream): Unit =
    exportPGPData(publicKey, os, true, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP public key and close the output stream. */
  def export(publicKey: PGPPublicKey, os: OutputStream, armored: Boolean): Unit =
    exportPGPData(publicKey, os, armored, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP public key and close the output stream. */
  def export(publicKey: PGPPublicKey, os: OutputStream, armored: Boolean, compressed: Int): Unit =
    exportPGPData(publicKey, os, armored, compressed)
  /** Export PGP public key ring and close the output stream. */
  def export(publicKeyRing: PGPPublicKeyRing, os: OutputStream): Unit =
    exportPGPData(publicKeyRing, os, true, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP public key ring and close the output stream. */
  def export(publicKeyRing: PGPPublicKeyRing, os: OutputStream, armored: Boolean): Unit =
    exportPGPData(publicKeyRing, os, armored, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP public key ring and close the output stream. */
  def export(publicKeyRing: PGPPublicKeyRing, os: OutputStream, armored: Boolean, compressed: Int): Unit =
    exportPGPData(publicKeyRing, os, armored, compressed)
  /** Export PGP private key ring and close the output stream. */
  def export(privateKeyRing: PGPSecretKeyRing, os: OutputStream): Unit =
    exportPGPData(privateKeyRing, os, true, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP private key ring and close the output stream. */
  def export(privateKeyRing: PGPSecretKeyRing, os: OutputStream, armored: Boolean): Unit =
    exportPGPData(privateKeyRing, os, armored, CompressionAlgorithmTags.UNCOMPRESSED)
  /** Export PGP private key ring and close the output stream. */
  def export(privateKeyRing: PGPSecretKeyRing, os: OutputStream, armored: Boolean, compressed: Int): Unit =
    exportPGPData(privateKeyRing, os, armored, compressed)
  /** Export PGP data and close the output stream. */
  def exportPGPData(data: { def encode(os: OutputStream) }, os: OutputStream, armored: Boolean, compressed: Int) {
    // Bouncy Castle’s implementations don’t chain calls to close(),
    // so we have to keep track of the various streams.
    var outStream = os
    // Output the data as ASCII, otherwise it will be output'ed as Binary
    val armoredStream = if (armored) Some(new ArmoredOutputStream(outStream)) else None
    armoredStream.foreach(outStream = _)
    // Compress the outgoing data if needed
    val compressedStream = if (compressed != CompressionAlgorithmTags.UNCOMPRESSED)
      Some(new PGPCompressedDataGenerator(compressed).open(outStream))
    else
      None
    compressedStream.foreach(outStream = _)
    try {
      data.encode(outStream)
      compressedStream.foreach { _.flush() }
      armoredStream.foreach { _.flush() }
      os.flush()
    } finally {
      compressedStream.foreach { _.close() }
      armoredStream.foreach { _.close() }
      os.close()
    }
  }
  /** Import PGP data. */
  def importPGPData(is: InputStream): Stream[AnyRef] = {
    val streamWithMarkSupported = if (is.markSupported()) is else new BufferedInputStream(is)
    streamWithMarkSupported.mark(4096)
    val PGPFactory = try {
      val compressed = new PGPCompressedData(new BCPGInputStream(PGPUtil.getDecoderStream(streamWithMarkSupported)))
      new PGPObjectFactory(PGPUtil.getDecoderStream(compressed.getDataStream()), new BcKeyFingerprintCalculator())
    } catch {
      case e: Throwable ⇒
        streamWithMarkSupported.reset()
        new PGPObjectFactory(PGPUtil.getDecoderStream(streamWithMarkSupported), new BcKeyFingerprintCalculator())
    }
    Option(PGPFactory.nextObject()) match {
      case Some(element) ⇒ Stream.cons(element, Option(PGPFactory.nextObject()).toStream)
      case None ⇒ Stream.empty
    }
  }
  /** Import PGP public key and close the input stream. */
  def importPGPPublicKey(is: InputStream): PGPPublicKey = try {
    importPGPData(is).find { element ⇒ element.isInstanceOf[PGPPublicKey] || element.isInstanceOf[PGPPublicKeyRing] } match {
      case Some(publicKey: PGPPublicKey) ⇒ publicKey
      case Some(publicKeyRing: PGPPublicKeyRing) ⇒ publicKeyRing.getPublicKey()
      case _ ⇒ throw new NoSuchElementException("PGP public key not found.")
    }
  } finally is.close()
  /** Import PGP public key ring and close the input stream. */
  def importPGPPublicKeyRing(is: InputStream): PGPPublicKeyRing = try {
    importPGPData(is).find { _.isInstanceOf[PGPPublicKeyRing] } match {
      case Some(publicKeyRing: PGPPublicKeyRing) ⇒ publicKeyRing
      case _ ⇒ throw new NoSuchElementException("PGP public keyring not found.")
    }
  } finally is.close()
  /** Import PGP private key ring and close the input stream. */
  def importPGPSecretKeyRing(is: InputStream): PGPSecretKeyRing = try {
    importPGPData(is).find { _.isInstanceOf[PGPSecretKeyRing] } match {
      case Some(privateKeyRing: PGPSecretKeyRing) ⇒ privateKeyRing
      case _ ⇒ throw new NoSuchElementException("PGP private keyring not found.")
    }
  } finally is.close()
}
