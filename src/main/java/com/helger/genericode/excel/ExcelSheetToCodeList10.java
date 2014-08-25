/**
 * Copyright (C) 2006-2014 phloc systems (www.phloc.com)
 * Copyright (C) 2014 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.genericode.excel;

import java.net.URI;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.poi.ss.usermodel.Sheet;

import com.helger.commons.string.StringHelper;
import com.helger.genericode.Genericode10Utils;
import com.helger.genericode.v10.Annotation;
import com.helger.genericode.v10.AnyOtherContent;
import com.helger.genericode.v10.CodeListDocument;
import com.helger.genericode.v10.Column;
import com.helger.genericode.v10.ColumnSet;
import com.helger.genericode.v10.Identification;
import com.helger.genericode.v10.Key;
import com.helger.genericode.v10.ObjectFactory;
import com.helger.genericode.v10.Row;
import com.helger.genericode.v10.SimpleCodeList;
import com.helger.genericode.v10.UseType;
import com.helger.genericode.v10.Value;
import com.helger.poi.excel.ExcelReadUtils;

/**
 * A utility class to convert a simple Excel sheet into a code list v1.0
 * consisting of simple values. Please note that merged cells are currently not
 * supported!
 * 
 * @author Philip Helger
 */
@Immutable
public final class ExcelSheetToCodeList10
{
  private static final QName QNAME_ANNOTATION = new QName ("urn:phloc.com:schemas:genericode-ext", "info");

  private ExcelSheetToCodeList10 ()
  {}

  @Nonnull
  public static CodeListDocument convertToSimpleCodeList (@Nonnull final Sheet aExcelSheet,
                                                          @Nonnull final ExcelReadOptions <UseType> aReadOptions,
                                                          @Nonnull final String sCodeListName,
                                                          @Nonnull final String sCodeListVersion,
                                                          @Nonnull final URI aCanonicalUri,
                                                          @Nonnull final URI aCanonicalVersionUri,
                                                          @Nullable final URI aLocationURI)
  {
    if (aExcelSheet == null)
      throw new NullPointerException ("sheet");
    if (aReadOptions == null)
      throw new NullPointerException ("readOptions");

    final ObjectFactory aFactory = new ObjectFactory ();
    final CodeListDocument ret = aFactory.createCodeListDocument ();

    // create annotation
    final Annotation aAnnotation = aFactory.createAnnotation ();
    final AnyOtherContent aContent = aFactory.createAnyOtherContent ();
    aContent.getAny ().add (new JAXBElement <String> (QNAME_ANNOTATION,
                                                      String.class,
                                                      null,
                                                      "Automatically created by phloc-genericode. Do NOT edit."));
    aAnnotation.setAppInfo (aContent);
    ret.setAnnotation (aAnnotation);

    // create identification
    final Identification aIdentification = aFactory.createIdentification ();
    aIdentification.setShortName (Genericode10Utils.createShortName (sCodeListName));
    aIdentification.setVersion (sCodeListVersion);
    aIdentification.setCanonicalUri (aCanonicalUri.toString ());
    aIdentification.setCanonicalVersionUri (aCanonicalVersionUri.toString ());
    if (aLocationURI != null)
      aIdentification.getLocationUri ().add (aLocationURI.toString ());
    ret.setIdentification (aIdentification);

    // create columns
    final List <ExcelReadColumn <UseType>> aExcelColumns = aReadOptions.getAllColumns ();
    final ColumnSet aColumnSet = aFactory.createColumnSet ();
    for (final ExcelReadColumn <UseType> aExcelColumn : aExcelColumns)
    {
      // Read short name (required)
      final String sShortName = aExcelSheet.getRow (aReadOptions.getLineIndexShortName ())
                                           .getCell (aExcelColumn.getIndex ())
                                           .getStringCellValue ();

      // Read long name (optional)
      String sLongName = null;
      if (aReadOptions.getLineIndexLongName () >= 0)
        sLongName = aExcelSheet.getRow (aReadOptions.getLineIndexLongName ())
                               .getCell (aExcelColumn.getIndex ())
                               .getStringCellValue ();

      // Create Genericode column set
      final Column aColumn = Genericode10Utils.createColumn (aExcelColumn.getColumnID (),
                                                             aExcelColumn.getUseType (),
                                                             sShortName,
                                                             sLongName,
                                                             aExcelColumn.getDataType ());

      // add column
      aColumnSet.getColumnChoice ().add (aColumn);

      if (aExcelColumn.isKeyColumn ())
      {
        // Create key definition
        final Key aKey = Genericode10Utils.createKey (aExcelColumn.getColumnID () + "Key",
                                                      sShortName,
                                                      sLongName,
                                                      aColumn);

        // Add key
        aColumnSet.getKeyChoice ().add (aKey);
      }
    }
    ret.setColumnSet (aColumnSet);

    // Read items
    final SimpleCodeList aSimpleCodeList = aFactory.createSimpleCodeList ();

    // Determine the row where reading should start
    int nRowIndex = aReadOptions.getLinesToSkip ();
    while (true)
    {
      // Read a single excel row
      final org.apache.poi.ss.usermodel.Row aExcelRow = aExcelSheet.getRow (nRowIndex++);
      if (aExcelRow == null)
        break;

      // Create Genericode row
      final Row aRow = aFactory.createRow ();
      for (final ExcelReadColumn <UseType> aExcelColumn : aExcelColumns)
      {
        final String sValue = ExcelReadUtils.getCellValueString (aExcelRow.getCell (aExcelColumn.getIndex ()));
        if (StringHelper.hasText (sValue) || aExcelColumn.getUseType () == UseType.REQUIRED)
        {
          // Create a single value in the current row
          final Value aValue = aFactory.createValue ();
          aValue.setColumnRef (Genericode10Utils.getColumnOfID (aColumnSet, aExcelColumn.getColumnID ()));
          aValue.setSimpleValue (Genericode10Utils.createSimpleValue (sValue));
          aRow.getValue ().add (aValue);
        }
      }
      aSimpleCodeList.getRow ().add (aRow);
    }
    ret.setSimpleCodeList (aSimpleCodeList);

    return ret;
  }
}