/*
 * Copyright 2011-2017 CaboLabs Health Informatics
 *
 * The EHRServer was designed and developed by Pablo Pazos Gutierrez <pablo.pazos@cabolabs.com> at CaboLabs Health Informatics (www.cabolabs.com).
 *
 * You can't remove this notice from the source code, you can't remove the "Powered by CaboLabs" from the UI, you can't remove this notice from the window that appears then the "Powered by CaboLabs" link is clicked.
 *
 * Any modifications to the provided source code can be stated below this notice.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cabolabs.ehrserver.query

import com.cabolabs.ehrserver.ehr.clinical_documents.*
import com.cabolabs.ehrserver.ehr.clinical_documents.data.*
import grails.util.Holders
import com.cabolabs.ehrserver.query.datatypes.*
import com.cabolabs.security.User
import com.cabolabs.util.DateParser

/**
 * Parametros n1 de la query:
 *  - ehrUid    (== compoIndex.ehrUid)
 *  - fromDate (<= compoIndex.startDate)
 *  - toDate   (>= compoIndex.startDate)
 *  - archetypeId (es parametro si no se especifica qarchetypeId en Query)
 *
 * Parametros n2 de la query:
 *  - valores para cada DataCriteria (el tipo del valor depende del tipo del RM en DataCriteria.path)
 *
 * @author pab
 *
 * TODO: crear un servicio que devuelva la definicion de una consulta
 *       con nombres, tipos y obligatoriedad de parametros.
 *
 */
class Query {

   String uid = java.util.UUID.randomUUID() as String

   // Describe lo que hace la query
   String name
   
   // queryByData (composition) o queryData (datavalue)
   // lo que los diferencia es el resultado: composiciones o datos asociados a paths
   String type
   
   // Sino se especifica, por defecto es xml
   String format = 'xml'
   
   // Filter by templateId (this is the document type)
   String templateId
   
   // Si la consulta es de datos, se filtra por indices de nivel 1 y se usa DataGet para especificar que datos se quieren en el resultado.
   // Si la consulta es de compositions, se filtra por indices de nivel 1 y tambien por nivel 2 (para n2 se usa DataCriteria)
   // Los filtros/criterios de n1 y de n2 son parametros de la query.
   List select = []
   List where = []
   static hasMany = [select: DataGet, where: DataCriteria]
   
   // For composition queries with criteria in where
   String criteriaLogic = 'AND' // AND or OR
   
   // null, composition o path
   // Sirve para agrupar datos:
   //  composition: sirve para mostrar tablas, donde cada fila es una composition
   //  path: sirve para armar series de valores para graficar
   String group = 'none'
   
   // https://github.com/ppazos/cabolabs-ehrserver/issues/340
   User author
   
   // true => shared with all the organizations
   boolean isPublic
   
   // org.codehaus.groovy.grails.web.json.JSONObject implementa Map
   static def newInstance(org.codehaus.groovy.grails.web.json.JSONObject json)
   {
      //println "Query.construct JSON: "+ json.toString()
      /*
       * Query.construct JSON:
       * {
       *   "select":[],
       *   "name":"popoop",
       *   "group":"none",
       *   "where":[
       *     {"id":1,"magnitudeValues":["1","2"],
       *      "archetypeId":"openEHR-EHR-OBSERVATION.blood_pressure.v1",
       *      "path":"/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value",
       *      "unitsOperand":"eq","class":"DataCriteriaDV_QUANTITY",
       *      "magnitudeOperand":"between","rmTypeName":"DV_QUANTITY",
       *      "unitsValues":["mmHg"]
       *     }
       *   ],
       *   "type":"composition","id_gen":1
       * }
       */
      
      //println json.name
      //println json.get('name')
      //println json['name']
      
      def query = new Query()
      
      query.updateInstance(json)
      
      return query
   }
   
   /**
    * used by updateInstance to create a list of dates from a string or list of strings entered in the query creation ui.
    * @return
    */
   def dateValues(criteriaValuesFromUI)
   {
      def dateValues = []
      if (criteriaValuesFromUI instanceof String)
      {
         //println "try to parse "+ criteria.valueValue
         def dateValue = DateParser.tryParse(criteriaValuesFromUI)
         dateValues << dateValue
      }
      else // criteria.valueValue is a list
      {
         criteriaValuesFromUI.each { stringDateValue ->
            dateValues << DateParser.tryParse(stringDateValue)
         }
      }
      return dateValues
   }
   
   /**
    * For edit/update.
    */
   def updateInstance(org.codehaus.groovy.grails.web.json.JSONObject json)
   {
      this.name       = json['name']
      this.type       = json['type']
      this.isPublic   = json['isPublic']
      this.format     = ( json['format'] ) ? json['format'] : 'xml' 
      this.templateId = json['template_id']
      
      if (this.type == 'composition')
      {
         this.criteriaLogic = json['criteriaLogic']

         this.where.each {
            it.delete()
         }
         this.where.clear() // remove criterias before adding current ones
         
         def condition
         json.where.each { criteria ->
         
            // removes the version for the archetype id and saves it
            if (criteria.allowAnyArchetypeVersion)
            {
               criteria.archetypeId = criteria.archetypeId.replaceAll(/\.v(\d)*/, '')
            }
            
            println "Criteria "+ criteria
            
            switch (criteria['class']) {
               case 'DataCriteriaDV_QUANTITY':
                  def magnitudeValue = []
                  if (criteria.magnitudeValue instanceof String)
                  {
                     magnitudeValue << new Double(criteria.magnitudeValue)
                  }
                  else
                  {
                     criteria.magnitudeValue.each {
                        magnitudeValue << new Double(it)
                     }
                  }
                  
                  criteria.magnitudeValue = magnitudeValue
                  condition = new DataCriteriaDV_QUANTITY(criteria)
               break
               case 'DataCriteriaDV_CODED_TEXT':
                  condition = new DataCriteriaDV_CODED_TEXT(criteria)
               break
               case 'DataCriteriaDV_TEXT':
                  condition = new DataCriteriaDV_TEXT(criteria)
               break
               case 'DataCriteriaDV_DATE_TIME':

                  def dateValues = dateValues(criteria.valueValue)
                  
                  // Set the values converted to Date
                  criteria.valueValue = dateValues
                  condition = new DataCriteriaDV_DATE_TIME(criteria)
               break
               case 'DataCriteriaDV_DATE':

                  def dateValues = dateValues(criteria.valueValue)
                  
                  // Set the values converted to Date
                  criteria.valueValue = dateValues
                  condition = new DataCriteriaDV_DATE(criteria)
               break
               case 'DataCriteriaDV_BOOLEAN':
                  condition = new DataCriteriaDV_BOOLEAN(criteria)
               break
               case 'DataCriteriaDV_COUNT':
                  condition = new DataCriteriaDV_COUNT(criteria)
               break
               case 'DataCriteriaDV_PROPORTION':
                  condition = new DataCriteriaDV_PROPORTION(criteria)
               break
               case 'DataCriteriaDV_ORDINAL':
                  condition = new DataCriteriaDV_ORDINAL(criteria)
               break
               case 'DataCriteriaDV_DURATION':
                  condition = new DataCriteriaDV_DURATION(criteria)
               break
               case 'DataCriteriaDV_IDENTIFIER':
                  condition = new DataCriteriaDV_IDENTIFIER(criteria)
               break
               case 'DataCriteriaDV_MULTIMEDIA':
                  condition = new DataCriteriaDV_MULTIMEDIA(criteria)
               break
               case 'DataCriteriaDV_PARSABLE':
                  condition = new DataCriteriaDV_PARSABLE(criteria)
               break
               case 'DataCriteriaString':
                  condition = new DataCriteriaString(criteria)
               break
               case 'DataCriteriaLOCATABLE_REF':
                  condition = new DataCriteriaLOCATABLE_REF(criteria)
               break
            }

            this.addToWhere(condition)
         }
      }
      else
      {
         this.group = json['group']
         
         this.select.each {
            it.delete()
         }
         this.select.clear()
         
         json.select.each { projection ->
            
            // removes the version for the archetype id and saves it
            if (projection.allow_any_archetype_version)
            {
               projection.archetype_id = projection.archetype_id.replaceAll(/\.v(\d)*/, '') //projection.archetype_id.take(projection.archetype_id.lastIndexOf('.'))
            }
            
            this.addToSelect(
               new DataGet(archetypeId: projection.archetype_id, 
                           path:        projection.path, 
                           rmTypeName:  projection.rmTypeName,
                           allowAnyArchetypeVersion: projection.allow_any_archetype_version)
            )
         }
      }
   }
   
   
   String toString ()
   {
      return "id: "+ this.id +", name: "+ this.name +", type: "+ this.type +", where: "+ this.where.toString() 
   }
   
   
   static constraints = {
      
      // para guardar la query debe tener nombre
      name(nullable:false, blank:false)
      
      // No creo que le guste null en inList, le pongo ''
      group(inList:['none', 'composition', 'path'])
      criteriaLogic(nullable: true)
      format(inList:['xml','json'])
      type(inList:['composition','datavalue'])
      
      templateId(nullable:true)
   }
   
   static mapping = {
      group column: 'dg_group' // group es palabra reservada de algun dbms
      select cascade: "all-delete-orphan" // cascade delete
      where cascade: "all-delete-orphan" // cascade delete
   }
   
   def beforeInsert() {
      if (this.type == 'datavalue') this.criteriaLogic = null
   }

   def beforeUpdate() {
      if (this.type == 'datavalue') this.criteriaLogic = null
   }
   
   def execute(String ehrUid, Date from, Date to, String group, String organizationUid, int max, int offset)
   {
      if (this.type == 'datavalue') return executeDatavalue(ehrUid, from, to, group, organizationUid)
      return executeComposition(ehrUid, from, to, organizationUid, max, offset)
   }
   
   def executeDatavalue(String ehrUid, Date from, Date to, String group, String organizationUid)
   {
      //println "ehrUid: $ehrUid - organizationUid: $organizationUid"
      
      // Query data
      def res = DataValueIndex.withCriteria {
         
         // SELECT
         or { // matchea algun par archId+path
            this.select.each { dataGet ->
               
               and {
                  if (dataGet.allowAnyArchetypeVersion)
                     like('archetypeId', dataGet.archetypeId+'%') // version was removed on save
                  else
                     eq('archetypeId', dataGet.archetypeId)
                  eq('archetypePath', dataGet.path)
                  eq('rmTypeName', dataGet.rmTypeName) // gets a specific DV in case alteratives exist for the same arch and path
               }
            }
         }
         
         // WHERE level 1 filters
         owner { // CompositionIndex
            if (templateId) eq('templateId', templateId)
            if (ehrUid) eq('ehrUid', ehrUid) // Ya se verifico que viene el param y que el ehr existe
            if (organizationUid) eq('organizationUid', organizationUid)
            
            // event can use startTime, persistent uses timeCommitted to filter by date
            or {
              and {
                 eq('category', 'event')
                 if (from) ge('startTime', from) // greater or equal
                 if (to) le('startTime', to) // lower or equal
              }
              and {
                eq('category', 'persistent')
                if (from) ge('timeCommitted', from)
                if (to) le('timeCommitted', to)
              }
            }
            
            eq('lastVersion', true) // query only latest versions
         }
      }
      
      
      // Group
      // If group is not empty, use that, if not, use the query grouping
      if (!group) group = this.group
      
      if (group == 'composition')
      {
         res = queryDataGroupComposition(res, (!ehrUid))
      }
      else if (group == 'path')
      {
         res = queryDataGroupPath(res, (!ehrUid))
      }
      else
      {
         if (!ehrUid) res = res.groupBy { dvi -> dvi.owner.ehrUid }
      }
      
      return res
   }
   
   /**
    * Usada por queryData para agrupar por composition
    */
   private List queryDataGroupComposition(res, groupByEHR)
   {
      def resHeaders = [:]
      def dataidx
      
      // =========================================================================
      // TODO: obtener el nombre del arquetipo en cada path para usar de header
      // =========================================================================
      
      // Headers para la tabla: 1 col por path, y dentro de cada path 1 col por atributo del DataValue
      // h1: | path1 (DvQuantity) | path2 (DvCodedText) | ... |
      // h2: | magnitude | units  |   code   |  value   | ... |
      //
      // [
      //  path1: [ type:'DV_QUANTITY', attrs:['magnitude','units'] ],
      //  path2: [ type:'DV_CODED_TEXT', attrs:['code','value'],
      //  ...
      // ]
      
      // Usa ruta absoluta para agrupar.
      String absPath
      
      // If any archetype version is allowed, results is be grouped by the archid concept,
      // but maybe inside the data we can add the specific archetype with version.
      
      this.select.each { dataGet ->

         // PROBLEM: this gets just one alternative for the arch+path, so the type should be taken from the dataGet,
         // using the dataidx to get the name is correct because the name doesn't vary for the alternative contraints.
         // Lookup del tipo de objeto en la path para saber los nombres de los atributos
         // concretos por los cuales buscar (la path apunta a datavalue no a sus campos).
         if (dataGet.allowAnyArchetypeVersion)
         {
            absPath = dataGet.archetypeId +'.*'+ dataGet.path +'<'+dataGet.rmTypeName+'>'
            dataidx = ArchetypeIndexItem.findByArchetypeIdLikeAndPath(dataGet.archetypeId+'%', dataGet.path)
         }
         else
         {
            absPath = dataGet.archetypeId + dataGet.path +'<'+dataGet.rmTypeName+'>' // type added to avoid collisions between alternatives that will have the same absolute path
            dataidx = ArchetypeIndexItem.findByArchetypeIdAndPath(dataGet.archetypeId, dataGet.path)
         }
         
         resHeaders[absPath] = [:]
         resHeaders[absPath]['type'] = dataGet.rmTypeName // FIX to the PROBLEM above
         resHeaders[absPath]['name'] = dataidx.name
         
         // DataCriteria is used bellow because they have the attribute definitions per datatype.
         switch (dataGet.rmTypeName)
         {
            case 'DV_QUANTITY':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_QUANTITY.attributes() // ['magnitude', 'units']
            break
            case 'DV_CODED_TEXT':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_CODED_TEXT.attributes() // ['value', 'code']
            break
            case 'DV_ORDINAL':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_ORDINAL.attributes() // ['value', 'symbol_value', 'symbol_code']
            break
            case 'DV_TEXT':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_TEXT.attributes() // ['value']
            break
            case 'DV_DATE_TIME':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_DATE_TIME.attributes() // ['value']
            break
            case 'DV_DATE':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_DATE.attributes() // ['value']
            break
            case 'DV_BOOLEAN':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_BOOLEAN.attributes() // ['value']
            break
            case 'DV_COUNT':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_COUNT.attributes() // ['magnitude']
            break
            case 'DV_PROPORTION':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_PROPORTION.attributes() // ['numerator', 'denominator', 'type', 'precision']
            break
            case 'DV_DURATION':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_DURATION.attributes() // ['value', 'magnitude']
            break
            case 'DV_IDENTIFIER':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_IDENTIFIER.attributes()
            break
            case 'DV_MULTIMEDIA':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_MULTIMEDIA.attributes()
            break
            case 'DV_PARSABLE':
               resHeaders[absPath]['attrs'] = DataCriteriaDV_PARSABLE.attributes()
            break
            case 'String':
               resHeaders[absPath]['attrs'] = DataCriteriaString.attributes()
            break
            case 'LOCATABLE_REF':
               resHeaders[absPath]['attrs'] = DataCriteriaLOCATABLE_REF.attributes()
            break
            default:
               throw new Exception("type "+dataGet.rmTypeName+" not supported")
         }
      }
      
      
      // Filas de la tabla
      def resGrouped
      
      if (groupByEHR)
      {
         resGrouped = queryDataGroupByEHRAndComposition(res, resHeaders)
      }
      else
      {
         resGrouped = queryDataGroupByComposition(res, resHeaders)
      }
      
      return [resHeaders, resGrouped]
      
   } // queryDataGroupComposition
   
   
   private Map queryDataGroupByEHRAndComposition(res, resHeaders)
   {
      def resGrouped = [:]
      def rows = res.groupBy { dvi -> dvi.owner.ehrUid }
      
      rows.each { ehrUid, dvis ->
         
         resGrouped[ehrUid] = queryDataGroupByComposition(dvis, resHeaders)
      }
      
      return resGrouped
   }
   
   private Map queryDataGroupByComposition(res, resHeaders)
   {
      def coldvis
      def colValues // lista de valores de una columna
      def uid
      def resGrouped = [:]
      def elem
      def tmp_arch_id
      def _absPathVersion // abstract path with specific arch id version
      
      // dvis por composition (Map[compo.id] = [dvi, dvi, ...])
      def rows = res.groupBy { it.owner.id }
      
      rows.each { compoId, dvis ->
         
         uid = dvis[0].owner.uid
         
         resGrouped[uid] = [:]
         
         // Datos de la composition
         // FIXME: deberia haber por lo menos un dvi, sino esto da error
         if (dvis[0].owner.category == 'event')
            resGrouped[uid]['date'] = dvis[0].owner.startTime
         else
            resGrouped[uid]['date'] = dvis[0].owner.timeCommitted
            
         //resGrouped[compoId]['uid']  = dvis[0].owner.uid
         resGrouped[uid]['cols'] = []
         
         // Las columnas no incluyen la path porque se corresponden en el indice con la path en resHeaders
         // Cada columna de la fila
         
         resHeaders.each { _absPath, colData -> // colData = [type:'XX', attrs:['cc','vv']]
            
            // values contain 1 element if there is only 1 DV occurrence, or many elements
            // as occurrences of that node exist.
            
            // colValues.path should be the one on the OPT that constraints the compo {uid},
            // and include the archetype id with a specific version, even if the 
            // query has any vesion allowed. The path portion is the same as the _absPath.
            // This can't be taken from the data, since results can be empty.
            
            _absPathVersion = dvis[0].owner.archetypeId + _absPath[_absPath.indexOf("/")..-1]
            colValues = [type: colData['type'], path: _absPathVersion, values:[]] // pongo la path para debug
            
            // dvi para la columna actual
            // pueden ser varios si hay multiples ocurrencias del mismo nodo
            
            tmp_arch_id = _absPath.take(_absPath.indexOf('/'))
            if (tmp_arch_id.endsWith('.*')) // allowAnyArchetypeVersion?
            {
               coldvis = dvis.findAll{ (it.archetypeId.replaceAll(/\.v(\d)*/, '.*') + it.archetypePath + '<'+ it.rmTypeName +'>') == _absPath && it.owner.id == compoId}
            }
            else
            {
               coldvis = dvis.findAll{ (it.archetypeId + it.archetypePath + '<'+ it.rmTypeName +'>') == _absPath && it.owner.id == compoId}
            }
            coldvis.each { dvi ->
            
               elem = [:]
            
               // Datos de cada path seleccionada dentro de la composition
               switch (colData['type'])
               {
                  case 'DV_QUANTITY':
                     elem['magnitude'] = dvi.magnitude
                     elem['units'] = dvi.units
                  break
                  case 'DV_CODED_TEXT':
                     elem['value'] = dvi.value
                     elem['code'] = dvi.code
                  break
                  case 'DV_TEXT':
                     elem['value'] = dvi.value
                  break
                  case ['DV_DATE_TIME', 'DV_DATE']:
                     elem['value'] = dvi.value
                  break
                  case 'DV_BOOLEAN':
                     elem['value'] = dvi.value
                  break
                  case 'DV_COUNT':
                     elem['magnitude'] = dvi.magnitude
                  break
                  case 'DV_PROPORTION':
                     elem['numerator'] = dvi.numerator
                     elem['denominator'] = dvi.denominator
                     elem['type'] = dvi.type
                     elem['precision'] = dvi.precision
                  break
                  case 'DV_ORDINAL':
                     elem['value'] = dvi.value
                     elem['symbol_value'] = dvi.symbol_value
                     elem['symbol_code'] = dvi.symbol_code
                     elem['symbol_terminology_id'] = dvi.symbol_terminology_id
                  break
                  case 'DV_DURATION':
                     elem['value'] = dvi.value
                     elem['magnitude'] = dvi.magnitude
                  break
                  case 'DV_IDENTIFIER':
                     elem['id'] = dvi.identifier // needed to change the DV_IDENTIFIER.id attr name to identifier because it is used by grails for the identity.
                     elem['type'] = dvi.type
                     elem['issuer'] = dvi.issuer
                     elem['assigner'] = dvi.assigner
                  break
                  case 'DV_MULTIMEDIA':
                     elem['mediaType'] = dvi.mediaType
                     elem['size'] = dvi.size
                     elem['alternateText'] = dvi.alternateText
                  break
                  case 'DV_PARSABLE':
                     elem['value'] = dvi.value
                     elem['formalism'] = dvi.formalism
                  break
                  case 'String':
                     elem['value'] = dvi.value
                  break
                  case 'LOCATABLE_REF':
                     elem['locatable_ref_path'] = dvi.locatable_ref_path
                  break
                  default:
                     throw new Exception("type "+colData['type']+" not supported")
               }
               
               colValues.values << elem
            } // each dvi
            
            resGrouped[uid]['cols'] << colValues
         }
      }
      
      return resGrouped
   }
   
   /**
    * Usada por queryData para agrupar por path
    */
   private Map queryDataGroupPath(res, groupByEHR)
   {
      if (groupByEHR)
      {
         return queryDataGroupByEHRAndPath(res)
      }

      return queryDataGroupByPath(res)
      
   } // queryDataGroupPath
   
   
   private Map queryDataGroupByEHRAndPath(res)
   {
      def resGrouped = [:]
      def cols = res.groupBy { dvi -> dvi.owner.ehrUid }
      
      cols.each { ehrUid, dvis ->
         
         resGrouped[ehrUid] = queryDataGroupByPath(dvis)
      }
      
      return resGrouped
   }
   
   private Map queryDataGroupByPath(res)
   {
      def dataidx
      
      // Columnas de la tabla (series)
      def resGrouped = [:]

      // Estructura auxiliar para recorrer y armar la agrupacion en series.
      def cols = res.groupBy { dvi ->
         dvi.archetypeId + dvi.archetypePath +'<'+dvi.rmTypeName+'>'
      }
      
      String absPath // absolute path used to group
      Date dviDate
      def tmp_arch_id
      def elems
      
      this.select.each { dataGet ->
         
         // PROBLEM: this gets just one alternative for the arch+path, so the type should be taken from the dataGet,
         // using the dataidx to get the name is correct because the name doesn't vary for the alternative contraints.
         // Lookup del tipo de objeto en la path para saber los nombres de los atributos
         // concretos por los cuales buscar (la path apunta a datavalue no a sus campos).
         
         if (dataGet.allowAnyArchetypeVersion)
         {
            tmp_arch_id = dataGet.archetypeId +'.*'
            absPath = dataGet.archetypeId +'.*'+ dataGet.path +'<'+dataGet.rmTypeName+'>'
            dataidx = ArchetypeIndexItem.findByArchetypeIdLikeAndPath(dataGet.archetypeId+'%', dataGet.path)
         }
         else
         {
            tmp_arch_id = dataGet.archetypeId
            absPath = dataGet.archetypeId + dataGet.path +'<'+dataGet.rmTypeName+'>' // type added to avoid collisions between alternatives that will have the same absolute path
            dataidx = ArchetypeIndexItem.findByArchetypeIdAndPath(dataGet.archetypeId, dataGet.path)
         }

         resGrouped[absPath] = [:]
         resGrouped[absPath]['type'] = dataGet.rmTypeName // type va en cada columna
         resGrouped[absPath]['name'] = dataidx.name // name va en cada columna, nombre asociado a la path por la que se agrupa
         resGrouped[absPath]['serie'] = []
         
         // absPath can have any archetype version .* but cols are grouped by
         // specific versions of arcehtypes, so cols[absPath can be empty].
         // Need to use matches
         
         //println "COLS to group by path "+ cols
         
         if (tmp_arch_id.endsWith('.*'))
            elems = cols.find { it.key.replaceAll(/\.v(\d)*/, '.*') == absPath }.value
         else
            elems = cols[absPath]
            
         //println "ELEMS group by path "+ elems
         
         elems.each { dvi ->
            
            //println "dvi: "+ dvi + " rmTypeName: "+ dataidx.rmTypeName
            
            if (dvi.owner.category == 'event')
               dviDate = dvi.owner.startTime
            else
               dviDate = dvi.owner.timeCommitted
            
            
            // Datos de cada path seleccionada dentro de la composition
            switch (dataGet.rmTypeName)
            {
               case 'DV_QUANTITY': // FIXME: this is a bug on adl parser it uses Java types instead of RM ones
                  resGrouped[absPath]['serie'] << [magnitude:    dvi.magnitude,
                                                   units:        dvi.units,
                                                   date:         dviDate]
               break
               case 'DV_CODED_TEXT':
                  resGrouped[absPath]['serie'] << [code:         dvi.code,
                                                   value:        dvi.value,
                                                   date:         dviDate]
               break
               case 'DV_ORDINAL':
                  resGrouped[absPath]['serie'] << [value:        dvi.value,
                                                   symbol_value: dvi.symbol_value,
                                                   symbol_code:  dvi.symbol_code,
                                                   symbol_terminology_id: dvi.symbol_terminology_id,
                                                   date:         dviDate]
               break
               case 'DV_TEXT':
                  resGrouped[absPath]['serie'] << [value:        dvi.value,
                                                   date:         dviDate]
               break
               case ['DV_DATE_TIME', 'DV_DATE']:
                  resGrouped[absPath]['serie'] << [value:        dvi.value,
                                                   date:         dviDate]
               break
               case 'DV_BOOLEAN':
                  resGrouped[absPath]['serie'] << [value:        dvi.value,
                                                   date:         dviDate]
               break
               case 'DV_COUNT':
                  resGrouped[absPath]['serie'] << [magnitude:    dvi.magnitude,
                                                   date:         dviDate]
               break
               case 'DV_PROPORTION':
                  resGrouped[absPath]['serie'] << [numerator:    dvi.numerator,
                                                   denominator:  dvi.denominator,
                                                   type:         dvi.type,
                                                   precision:    dvi.precision,
                                                   date:         dviDate]
               break
               case 'DV_DURATION':
                  resGrouped[absPath]['serie'] << [value:        dvi.value,
                                                   magnitude:    dvi.magnitude,
                                                   date:         dviDate]
               break
               case 'DV_IDENTIFIER':
                  resGrouped[absPath]['serie'] << [id:           dvi.identifier, // needed to change the DV_IDENTIFIER.id attr name to identifier because it is used by grails for the identity.
                                                   type:         dvi.type,
                                                   issuer:       dvi.issuer,
                                                   assigner:     dvi.assigner,
                                                   date:         dviDate]
               break
               case 'DV_MULTIMEDIA':
                  resGrouped[absPath]['serie'] << [mediaType:     dvi.mediaType,
                                                   size:          dvi.size,
                                                   alternateText: dvi.alternateText,
                                                   date:          dviDate]
               break
               case 'DV_PARSABLE':
                  resGrouped[absPath]['serie'] << [value:         dvi.value,
                                                   formalism:     dvi.formalism,
                                                   date:          dviDate]
               break
               case 'String':
                  resGrouped[absPath]['serie'] << [value:         dvi.value,
                                                   date:          dviDate]
               break
               case 'LOCATABLE_REF':
                  resGrouped[absPath]['serie'] << [locatable_ref_path: dvi.locatable_ref_path,
                                                   date:          dviDate]
               break
               default:
                  throw new Exception("type "+dataGet.rmTypeName+" not supported")
            }
            
            // para cada fila quiero fecha y uid de la composition
         }
      }
      
      return resGrouped
   }
   
   
   def executeComposition(String ehrUid, Date from, Date to, String organizationUid, int max, int offset)
   {
      def formatterDateDB = new java.text.SimpleDateFormat( Holders.config.app.l10n.db_date_format )
      
      // Armado de la query
      String q = "FROM CompositionIndex ci WHERE ci.lastVersion=true AND " // Query only latest versions
      
      // ===============================================================
      // Criteria nivel 1 ehrUid
      if (ehrUid) q += "ci.ehrUid = '" + ehrUid + "' AND "
      if (organizationUid) q += "ci.organizationUid = '" + organizationUid + "' AND "
       
      // Filter by templateId
      if (this.templateId) q += "ci.templateId = '" + this.templateId +"' AND "
      
      
      // Criterio de rango de fechas para ci.startTime (event), timeCommitted (persistent)
      // Formatea las fechas al formato de la DB
      String eventDateCriteria = "ci.category = 'event' AND "
      boolean hasEventDateCriteria = false
      if (from)
      {
         hasEventDateCriteria = true
         eventDateCriteria += "ci.startTime >= '"+ formatterDateDB.format( from ) +"'" // higher or equal
      }
      if (to)
      {
         if (hasEventDateCriteria) eventDateCriteria += " AND "
         hasEventDateCriteria = true
         eventDateCriteria += "ci.startTime <= '"+ formatterDateDB.format( to ) +"'" // lower or equal
      }
      
      String persistentDateCriteria = "ci.category = 'persistent' AND "
      boolean hasPersistentDateCriteria = false
      if (from)
      {
         hasPersistentDateCriteria = true
         persistentDateCriteria += "ci.timeCommitted >= '"+ formatterDateDB.format( from ) +"'" // higher or equal
      }
      if (to)
      {
         if (hasPersistentDateCriteria) persistentDateCriteria += " AND "
         hasPersistentDateCriteria = true
         persistentDateCriteria += "ci.timeCommitted <= '"+ formatterDateDB.format( to ) +"'" // lower or equal
      }
      
      if (hasEventDateCriteria && hasPersistentDateCriteria)
      {
         q += "(("+ eventDateCriteria +") OR ("+ persistentDateCriteria +")) AND "
      }
      else if (hasEventDateCriteria)
      {
         q += "("+ eventDateCriteria +") AND "
      }
      else if (hasPersistentDateCriteria)
      {
         q += "("+ persistentDateCriteria +") AND "
      }
      
      //
      // ===============================================================
      
      // Si no hay criterio, hace la busqueda solo por tipo de documento.
      // Sin este chequeo, se rompe la query porque sobra un " AND "
      if (!this.where)
      {
         q = q[0..-6] // quita el ultimo " AND ", es -6 porque -1 pone el puntero al final del string y luego hay que sacar 5 chars
      }
      else
      {
         q += '(' // exists blocks should be isolated to avoid bad AND/OR association
         
         /**
           * FIXME: issue #6
           * si en el create se verifican las condiciones para que a aqui no
           * llegue una path a un tipo que no corresponde, el error de tipo
           * no sucederia nunca, asi no hay que tirar except aca.
           */
         //def dataidx
         def idxtype
         this.where.eachWithIndex { dataCriteria, i ->
             
            // Aux to build the query FROM
            def fromMap = ['DataValueIndex': 'dvi']
             
            // Lookup del tipo de objeto en la path para saber los nombres de los atributos
            // concretos por los cuales buscar (la path apunta a datavalue no a sus campos).
             
            //println "archId "+ dataCriteria.archetypeId
            //println "path "+ dataCriteria.path
            
            // PROBLEM: for alternatives, this returns only one alternative DV
            //dataidx = ArchetypeIndexItem.findByArchetypeIdAndPath(dataCriteria.archetypeId, dataCriteria.path)
            //idxtype = dataidx?.rmTypeName
            
            // FIX to the problem: we have the DV in the DataCriteria
            idxtype = dataCriteria.rmTypeName
            
            // ================================================================
            // TODO:
            // Since GRAILS 2.4 it seems that exists can be done in Criteria,
            // should use that instead of HQL.
            // https://jira.grails.org/browse/GRAILS-9223
            // ================================================================
             
             
            // Subqueries sobre los DataValueIndex de los CompositionIndex
            q += " EXISTS ("
             
             // dvi.owner.id = ci.id
             // Asegura de que todos los EXISTs se cumplen para el mismo CompositionIndex
             // (los criterios se consideran AND, sin esta condicion es un OR y alcanza que
             // se cumpla uno de los criterios que vienen en params)
            def subq = "SELECT dvi.id FROM "
             
            //"  FROM DataValueIndex dvi" + // FROM is set below
            
            def where
            if (dataCriteria.allowAnyArchetypeVersion)
               where = $/WHERE dvi.owner.id = ci.id AND
                      dvi.archetypeId LIKE '${dataCriteria.archetypeId}%' AND
                      dvi.archetypePath = '${dataCriteria.path}'/$
            else
               where = $/WHERE dvi.owner.id = ci.id AND
                      dvi.archetypeId = '${dataCriteria.archetypeId}' AND
                      dvi.archetypePath = '${dataCriteria.path}'/$
             
            // Consulta sobre atributos del ArchetypeIndexItem dependiendo de su tipo
            switch (idxtype)
            {
               // ADL Parser bug: uses Java class names instead of RM Type Names...
               // FIXME: we are not working with ADL any more, the java types can be removed...
               case 'DV_DATE_TIME':
                   fromMap['DvDateTimeIndex'] = 'ddti'
                   where += " AND ddti.id = dvi.id "
               break
               case 'DV_DATE':
                  fromMap['DvDateIndex'] = 'dcdte'
                  where += " AND dcdte.id = dvi.id "
               break
               case 'DV_QUANTITY':
                   fromMap['DvQuantityIndex'] = 'dqi'
                   where += " AND dqi.id = dvi.id "
               break
               case 'DV_CODED_TEXT':
                   fromMap['DvCodedTextIndex'] = 'dcti'
                   where += " AND dcti.id = dvi.id "
               break
               case 'DV_TEXT':
                   fromMap['DvTextIndex'] = 'dti'
                   where += " AND dti.id = dvi.id "
               break
               case 'DV_ORDINAL':
                   fromMap['DvOrdinalIndex'] = 'dvol'
                   where += " AND dvol.id = dvi.id "
               break
               case 'DV_BOOLEAN':
                   fromMap['DvBooleanIndex'] = 'dbi'
                   where += " AND dbi.id = dvi.id "
               break
               case 'DV_COUNT':
                   fromMap['DvCountIndex'] = 'dci'
                   where += " AND dci.id = dvi.id "
               break
               case 'DV_PROPORTION':
                   fromMap['DvProportionIndex'] = 'dpi'
                   where += " AND dpi.id = dvi.id "
               break
               case 'DV_DURATION':
                  fromMap['DvDurationIndex'] = 'dduri'
                  where += " AND dduri.id = dvi.id "
               break
               case 'DV_IDENTIFIER':
                  fromMap['DvIdentifierIndex'] = 'dvidi'
                  where += " AND dvidi.id = dvi.id "
               break
               case 'DV_MULTIMEDIA':
                  fromMap['DvMultimediaIndex'] = 'dvmmd'
                  where += " AND dvmmd.id = dvi.id "
               break
               case 'DV_PARSABLE':
                  fromMap['DvParsableIndex'] = 'dpab'
                  where += " AND v.id = dvi.id "
               break
               case 'String':
                  fromMap['StringIndex'] = 'dstg'
                  where += " AND dstg.id = dvi.id "
               break
               case 'LOCATABLE_REF':
                  fromMap['LocatableRefIndex'] = 'dlor'
                  where += " AND dlor.id = dvi.id "
               break
               default:
                  throw new Exception("type $idxtype not supported")
            }
            
            where += " AND " + dataCriteria.toSQL() // important part: complex criteria to SQL, depends on the datatype
            
            fromMap.each { index, alias ->
                
               subq += index +' '+ alias +' , '
            }
            subq = subq.substring(0, subq.size()-2)
            subq += where
            
            q += subq
            q += ")" // closes exists (...
            
            
            // TEST
            // TEST
            //println "SUBQ DVI: "+ subq.replace("dvi.owner.id = ci.id AND ", "")
            //println DataValueIndex.executeQuery(subq.replace("dvi.owner.id = ci.id AND ", ""))
            
            
            //       EXISTS (
            //         SELECT dvi.id
            //         FROM ArchetypeIndexItem dvi
            //         WHERE dvi.owner.id = ci.id
            //               AND dvi.archetypeId = openEHR-EHR-COMPOSITION.encounter.v1
            //               AND dvi.path = /content/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value
            //               AND dvi.magnitude>140.0
            //       ) AND EXISTS (
            //         SELECT dvi.id
            //         FROM ArchetypeIndexItem dvi
            //         WHERE dvi.owner.id = ci.id
            //               AND dvi.archetypeId = openEHR-EHR-COMPOSITION.encounter.v1
            //               AND dvi.path = /content/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value
            //               AND dvi.magnitude<130.0
            //       ) AND EXISTS (
            //         SELECT dvi.id
            //         FROM ArchetypeIndexItem dvi
            //         WHERE dvi.owner.id = ci.id
            //               AND dvi.archetypeId = openEHR-EHR-COMPOSITION.encounter.v1
            //               AND dvi.path = /content/data[at0001]/origin
            //               AND dvi.value>20080101
            //       )
            
            
            // Agrega ANDs para los EXISTs, menos el ultimo
            if (i+1 < this.where.size()) q += ' '+ criteriaLogic +' ' // AND or OR
         }
         
         q += ')' // exists blocks should be isolated to avoid bad AND/OR association
      }
      
      println "HQL QUERY: \n" + q
      
      // default pagination
      if (!max)
      {
         max = 20
         offset = 0
      }
      
      def cilist = CompositionIndex.executeQuery( q, [offset:offset, max:max, readOnly:true] )
      
      return cilist
   }
   
   
   def getXML()
   {
      return this as grails.converters.XML
   }
   
   def getJSON()
   {
      return this as grails.converters.JSON
   }
}
