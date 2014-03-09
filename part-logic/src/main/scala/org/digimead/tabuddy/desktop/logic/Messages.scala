/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic

import java.util.ResourceBundle
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.NLS

/**
 * Resource bundle implementation.
 *
 * This code is directly evaluated in IDE (WindowBuilderPro).
 * Any runtime references that may prevent creation are prohibited.
 */
class Messages extends ResourceBundle {
  def getKeys() = new java.util.Enumeration[String] {
    private val iterator = Messages.T.messages.keys.iterator
    def hasMoreElements(): Boolean = iterator.hasNext
    def nextElement(): String = iterator.next()
  }
  protected def handleGetObject(key: String): Object = try {
    Messages.T.messages.get(key).
      getOrElse { Messages.log.error(s"'${key}' not found in ${this.getClass()}"); key }
  } catch {
    case e: Throwable ⇒
      key
  }
}

object Messages extends NLS with Loggable {
  val closeAllFiles_text = ""
  val closeFile_text = ""
  val creationError_text = ""
  val exportFile_text = ""
  val graphContentTitle = ""
  val graphContentTitleEmpty = ""
  val graph_closeDescriptionLong_text = ""
  val graph_closeDescriptionShort_text = ""
  val graph_close_text = ""
  val graph_deleteDescriptionLong_text = ""
  val graph_deleteDescriptionShort_text = ""
  val graph_delete_text = ""
  val graph_exportDescriptionLong_text = ""
  val graph_exportDescriptionShort_text = ""
  val graph_export_text = ""
  val graph_importDescriptionLong_text = ""
  val graph_importDescriptionShort_text = ""
  val graph_import_text = ""
  val graph_listDescriptionLong_text = ""
  val graph_listDescriptionShort_text = ""
  val graph_list_text = ""
  val graph_newDescriptionLong_text = ""
  val graph_newDescriptionShort_text = ""
  val graph_new_text = ""
  val graph_openDescriptionLong_text = ""
  val graph_openDescriptionShort_text = ""
  val graph_open_text = ""
  val graph_saveAsDescriptionLong_text = ""
  val graph_saveAsDescriptionShort_text = ""
  val graph_saveAs_text = ""
  val graph_saveDescriptionLong_text = ""
  val graph_saveDescriptionShort_text = ""
  val graph_save_text = ""
  val graph_showDescriptionLong_text = ""
  val graph_showDescriptionShort_text = ""
  val graph_show_text = ""
  val identifierIsEmpty_text = ""
  val importFile_text = ""
  val lblModelIdentificator_hint_text = ""
  val lblModelIdentificator_text = ""
  val lblModelLocation_hint_text = ""
  val localizedTypeSchemaDescription_text = ""
  val localizedTypeSchemaName_text = ""
  val locationIsAlreadyExists_text = ""
  val locationIsEmpty_text = ""
  val locationIsIncorrect_text = ""
  val modifyElementTemplateListDescriptionLong_text = ""
  val modifyElementTemplateListDescriptionShort_text = ""
  val modifyElementTemplateList_text = ""
  val modifyEnumerationListDescriptionLong_text = ""
  val modifyEnumerationListDescriptionShort_text = ""
  val modifyEnumerationList_text = ""
  val modifyTypeSchemaListDescriptionLong_text = ""
  val modifyTypeSchemaListDescriptionShort_text = ""
  val modifyTypeSchemaList_text = ""
  val modifyViewDefinitionListDescriptionLong_text = ""
  val modifyViewDefinitionListDescriptionShort_text = ""
  val modifyViewDefinitionList_text = ""
  val modifyViewFilterListDescriptionLong_text = ""
  val modifyViewFilterListDescriptionShort_text = ""
  val modifyViewFilterList_text = ""
  val modifyViewSortingListDescriptionLong_text = ""
  val modifyViewSortingListDescriptionShort_text = ""
  val modifyViewSortingList_text = ""
  val newFile_text = ""
  val openFile_text = ""
  val overViewPanelTitle_text = ""
  val properties_text = ""
  val saveAllFiles_text = ""
  val saveFile_text = ""
  val script_runDescriptionLong_text = ""
  val script_runDescriptionShort_text = ""
  val script_run_text = ""
  val shellTitleEmpty_text = ""
  val shellTitle_text = ""
  val wizardGraphNewPageOneDescription_text = ""
  val wizardGraphNewPageOneTitle_text = ""

  T.ranslate("org.digimead.tabuddy.desktop.logic.messages")
}
