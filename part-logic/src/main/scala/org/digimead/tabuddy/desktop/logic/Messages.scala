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
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ BaseResourceBundle, NLS }

/**
 * Resource bundle implementation.
 *
 * This code is directly evaluated in IDE (WindowBuilderPro).
 * Any runtime references that may prevent creation are prohibited.
 */
class Messages extends ResourceBundle with BaseResourceBundle {
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

object Messages extends NLS with XLoggable {
  val Adapter_enableCustomInitializationVector_text = "Enable custom initialization vector"
  val Adapter_lblDictionaryLength_text = "Dictionary length"
  val Adapter_lblInitializationVector_text = "Initialization vector"
  val Adapter_lblKeyLength_text = "Key length"
  val Adapter_lblKeyStrength_text = "Encryption strength"
  val Adapter_lblSecret_text = "Secret phrase"
  val Adapter_selectDictionaryLength_text = "select dictionary length..."
  val Adapter_selectDigestAlgorithm_text = "select digest algorithm..."
  val Adapter_selectKeyLength_text = "select key length..."
  val Adapter_selectKeyStrength_text = "select encryption strength..."
  val Adapter_selectXParameters_text = "'%s' %s parameters"
  val Adapter_txtInitializationVector_hint_text = "enter custom initialization vector..."
  val Adapter_txtInitializationVector_tip_text = "Custom initialization vector is a UTF8 string"
  val Adapter_txtSecret_hint_text = "enter secret phrase..."
  val Adapter_txtSecret_tip_text = "Secret phrase is a UTF8 string"
  val NewGraphWizardPageOne_btnLocation_text = "..."
  val NewGraphWizardPageOne_btnSignatureAcquire_text = "Configure"
  val NewGraphWizardPageOne_containerEncryptionIsNotDefined_text = "Container encryption is not defined"
  val NewGraphWizardPageOne_contentEncryptionIsNotDefined_text = "Content encryption is not defined"
  val NewGraphWizardPageOne_creationError_text = "Error while creating TA Buddy model."
  val NewGraphWizardPageOne_description_text = "Create a new graph."
  val NewGraphWizardPageOne_description_with_name_text = "Create the new graph '%s'."
  val NewGraphWizardPageOne_digestFreezeIsNotDefined_text = "Digest calculator is not defined"
  val NewGraphWizardPageOne_digestValidationIsDisabled_text = "digest validation is disabled"
  val NewGraphWizardPageOne_digestValidationIsNotSelected_text = "Digest validation is not chosen"
  val NewGraphWizardPageOne_digestValidationOptional_text = "digest validation is performed when possible"
  val NewGraphWizardPageOne_digestValidationRequired_text = "digest validation is required"
  val NewGraphWizardPageOne_formTitle_text = "Unnamed"
  val NewGraphWizardPageOne_lblContainerEncryption_text = "Container encryption"
  val NewGraphWizardPageOne_lblContentEncryption_text = "Content encryption"
  val NewGraphWizardPageOne_lblDigestAcquire_text = "Digest validator"
  val NewGraphWizardPageOne_lblDigestFreeze_text = "Digest calculator"
  val NewGraphWizardPageOne_lblIdentificator_hint_text = "Enter graph identifier. There are symbols of any language, digits or _ at the middle or at the end."
  val NewGraphWizardPageOne_lblIdentificator_text = "Identificator"
  val NewGraphWizardPageOne_lblLocation_hint_text = "Select the directory where the graph will be located."
  val NewGraphWizardPageOne_lblLocation_text = "Location"
  val NewGraphWizardPageOne_lblSerialization_text = "Payload serialization"
  val NewGraphWizardPageOne_lblSignatureAcquire_text = "Signature validator"
  val NewGraphWizardPageOne_lblSignatureFreeze_text = "Signature generator"
  val NewGraphWizardPageOne_sctnAcquire_text = "Acquire parameters"
  val NewGraphWizardPageOne_sctnCommon_text = "Common parameters"
  val NewGraphWizardPageOne_sctnFreeze_text = "Freeze parameters"
  val NewGraphWizardPageOne_selectContainerEncryption_text = "select container encryption..."
  val NewGraphWizardPageOne_selectContentEncryption_text = "select content encryption..."
  val NewGraphWizardPageOne_selectDigestMechanism_text = "select digest mechanism..."
  val NewGraphWizardPageOne_selectDigestValidation_text = "select digest validation..."
  val NewGraphWizardPageOne_selectSignatureMechanism_text = "select signature mechanism..."
  val NewGraphWizardPageOne_selectSignatureValidation_text = "select signature validation..."
  val NewGraphWizardPageOne_signatureFreezeIsNotDefined_text = "Signature generator is not defined"
  val NewGraphWizardPageOne_signatureValidationIsDisabled_text = "signature validation is disabled"
  val NewGraphWizardPageOne_signatureValidationIsNotSelected_text = "Signature validation is not chosen"
  val NewGraphWizardPageOne_title_text = "Graph"
  val NewGraphWizard_selectGraphLocation_text = "Select graph location"
  val NewGraphWizard_selectXParameters_text = "'%s' %s parameters"
  val NewGraphWizard_shellTitleEmpty_text = "New Graph"
  val NewGraphWizard_shellTitle_text = "New Graph - %s"
  val SimpleDigestAdapter_lblDigestAlgorithm_text = "Algorithm"
  val SimpleDigestAdapter_selectDigestAlgorithm_text = "select digest algorithm..."
  val autoresize_key = ""
  val closeAllFiles_text = ""
  val closeFile_text = ""
  val containerEncryption_text = "container encryption"
  val contentEncryption_text = "content encryption"
  val creationError_text = ""
  val digest_text = "digest"
  val encryption_text = "encryption"
  val exportFile_text = ""
  val graphContentTitle = ""
  val graphContentTitleEmpty = ""
  val graphImportDialogDescription_text = ""
  val graphImportDialogTitle_text = ""
  val graphImportDialog_text = ""
  val graphSelectionDialogDescription_text = ""
  val graphSelectionDialogTitle_text = ""
  val graphSelectionDialog_text = ""
  val graph_closeDescriptionLong_text = ""
  val graph_closeDescriptionShort_text = ""
  val graph_close_text = ""
  val graph_deleteDescriptionLong_text = ""
  val graph_deleteDescriptionShort_text = ""
  val graph_delete_text = ""
  val graph_exportDescriptionLong_text = ""
  val graph_exportDescriptionShort_text = ""
  val graph_export_text = ""
  val graph_historyDescriptionLong_text = ""
  val graph_historyDescriptionShort_text = ""
  val graph_history_text = ""
  val graph_importDescriptionLong_text = ""
  val graph_importDescriptionShort_text = ""
  val graph_import_text = ""
  val graph_listDescriptionLong_text = ""
  val graph_listDescriptionShort_text = ""
  val graph_list_text = ""
  val graph_metaDescriptionLong_text = ""
  val graph_metaDescriptionShort_text = ""
  val graph_meta_text = ""
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
  val locationIsAlreadyExists_text = "'%s' is already exists"
  val locationIsEmpty_text = "location is empty"
  val locationIsIncorrect_text = "unable to write to selected location"
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
  val noParameters = "No parameters."
  val openFile_text = ""
  val overViewPanelTitle_text = ""
  val parametersRequired = "parameters required"
  val parametersRequired_text = "parameters required"
  val properties_text = ""
  val saveAllFiles_text = ""
  val saveFile_text = ""
  val script_runDescriptionLong_text = ""
  val script_runDescriptionShort_text = ""
  val script_run_text = ""
  val selectToSetParameters = "select to set parameters"
  val shellTitleEmpty_text = ""
  val shellTitle_text = ""
  val showHiddenText_text = "show hidden text"
  val signature_text = "sigest"
  val wizardGraphNewPageOneDescription_text = ""
  val wizardGraphNewPageOneTitle_text = ""

  T.ranslate("org.digimead.tabuddy.desktop.logic.messages")
}
