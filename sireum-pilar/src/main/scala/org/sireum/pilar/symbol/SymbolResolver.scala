/*
Copyright (c) 2011-2013 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.pilar.symbol

import org.sireum.pilar.ast._
import org.sireum.pilar.symbol.SymbolTableMessage._
import org.sireum.util._

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait SymbolResolver {
  def dependency : MMap[String, MSet[String]]
  def locPropKey : Property.Key

  def addDependency(fromUri : FileResourceUri, toSD : SymbolDefinition) {
    if (toSD ? locPropKey) {
      import FileLocation._
      toSD.fileUriForEach { fileLoc : FileResourceUri =>
        if (fromUri != fileLoc) {
          dependency.getOrElseUpdate(fileLoc,
            msetEmpty[FileResourceUri]) += fromUri
        }
      }
    }
  }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait ConstResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val constResolver : VisitorFunction = {
    if (tables.constElementTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case NameExp(id) =>
          val paths = ivector(id.name)
          val key = H.symbolUri(H.CONST_ELEM_TYPE, paths)
          resolveConstElem(source, id, key, paths)
          false
        case AccessExp(NameExp(name), id) =>
          if (packageName != null) {
            val constName = name.name
            val constElementName = id.name
            val paths = ivector(constName, constElementName)
            val key = H.symbolUri(H.CONST_ELEM_TYPE, paths)
            if (!resolveConstElem(source, id, key, paths, name,
              ivector(constName))) {
              val paths = H.paths(packageName, constName, constElementName)
              val key = H.symbolUri(H.CONST_ELEM_TYPE, paths)
              resolveConstElem(source, id, key, paths, name,
                H.paths(packageName, constName))
            }
            false
          } else true
      }
    }
  }

  def resolveConstElem(source : Option[FileResourceUri],
                       constElemR : SymbolUser,
                       key : String,
                       constElemPaths : ISeq[String]) =
    tables.constElementTable.get(key) match {
      case Some(ce) =>
        H.symbolInit(constElemR, H.CONST_ELEM_TYPE, constElemPaths, key)
        source.foreach { addDependency(_, ce.name) }
      case _ =>
    }

  def resolveConstElem(source : Option[FileResourceUri],
                       constElemR : SymbolUser,
                       key : String, constElemPaths : ISeq[String],
                       constR : SymbolUser,
                       constPaths : => ISeq[String]) : Boolean =
    tables.constElementTable.get(key) match {
      case Some(ce) =>
        H.symbolInit(constR, H.CONST_TYPE, constPaths)
        H.symbolInit(constElemR, H.CONST_ELEM_TYPE, constElemPaths, key)
        source.foreach { addDependency(_, ce.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait EnumResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val enumResolver : VisitorFunction = {
    if (tables.enumTable.isEmpty && tables.enumElementTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case nts : NamedTypeSpec =>
          if (packageName != null) {
            val enumName = nts.name.name
            val paths = ivector(enumName)
            val key = H.symbolUri(H.ENUM_TYPE, paths)
            if (!resolveEnum(source, nts.name, key, paths)) {
              val paths = H.paths(packageName, enumName)
              val key = H.symbolUri(H.ENUM_TYPE, paths)
              resolveEnum(source, nts.name, key, paths)
            }
          } else {
            val enumName = nts.name.name
            val paths = ivector(enumName)
            val key = H.symbolUri(H.ENUM_TYPE, paths)
            resolveEnum(source, nts.name, key, paths)
          }
          false
        case NameExp(id) =>
          val paths = ivector(id.name)
          val key = H.symbolUri(H.ENUM_ELEM_TYPE, paths)
          resolveEnumElem(source, id, key, paths)
          false
        case AccessExp(NameExp(name), id) =>
          if (packageName != null) {
            val enumName = name.name
            val enumElementName = id.name
            val paths = ivector(enumName, enumElementName)
            val key = H.symbolUri(H.ENUM_ELEM_TYPE, paths)
            if (!resolveEnumElem(source, id, key, paths, name,
              ivector(enumName))) {
              val paths = H.paths(packageName, enumName, enumElementName)
              val key = H.symbolUri(H.ENUM_ELEM_TYPE, paths)
              resolveEnumElem(source, id, key, paths, name,
                H.paths(packageName, enumName))
            }
            false
          } else true
      }
    }
  }

  def resolveEnumElem(source : Option[FileResourceUri],
                      enumElemR : SymbolUser,
                      key : String, enumElemPaths : ISeq[String]) : Boolean =
    tables.enumElementTable.get(key) match {
      case Some(ee) =>
        H.symbolInit(enumElemR, H.ENUM_ELEM_TYPE, enumElemPaths, key)
        source.foreach { addDependency(_, ee.name) }
        true
      case _ =>
        false
    }

  def resolveEnumElem(source : Option[FileResourceUri],
                      enumElemR : SymbolUser,
                      key : String, enumElemPaths : ISeq[String],
                      enumR : SymbolUser,
                      enumPaths : => ISeq[String]) : Boolean =
    tables.enumElementTable.get(key) match {
      case Some(ee) =>
        H.symbolInit(enumR, H.ENUM_TYPE, enumPaths)
        H.symbolInit(enumElemR, H.ENUM_ELEM_TYPE, enumElemPaths, key)
        source.foreach { addDependency(_, ee.name) }
        true
      case _ =>
        false
    }

  def resolveEnum(source : Option[FileResourceUri],
                  enumR : SymbolUser,
                  key : String,
                  enumPaths : ISeq[String]) : Boolean =
    tables.enumTable.get(key) match {
      case Some(eds) =>
        H.symbolInit(enumR, H.RECORD_TYPE, enumPaths, key)
        source.foreach { addDependency(_, eds(0).name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait ExtensionResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val extensionResolver : VisitorFunction = {
    if (tables.extensionTable.isEmpty && tables.extensionElementTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = null;
      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case NamedTypeSpec(id, _) =>
          val paths = ivector(id.name)
          val key = H.symbolUri(H.TYPE_EXTENSION_TYPE, paths)
          resolveTypeExt(source, id, key, paths)
          false
        case nets : NamedExtTypeSpec =>
          if (packageName != null) {
            val extName = nets.extName
            val nameUser = nets.name
            val extTypeName = nameUser.name
            val paths = ivector(extName, extTypeName)
            val key = H.symbolUri(H.TYPE_EXTENSION_TYPE, paths)
            if (!resolveTypeExt(source, nameUser, key, paths)) {
              val paths = H.paths(packageName, extName, extTypeName)
              val key = H.symbolUri(H.TYPE_EXTENSION_TYPE, paths)
              resolveTypeExt(source, nameUser, key, paths)
            }
          }
          false
        case NameExp(id) =>
          val paths = ivector(id.name)
          val key = H.symbolUri(H.EXTENSION_ELEM_TYPE, paths)
          resolveExtElem(source, id, key, paths)
          false
        case AccessExp(NameExp(name), id) =>
          if (packageName != null) {
            val extName = name.name
            val expExtName = id.name
            val paths = ivector(extName, expExtName)
            val key = H.symbolUri(H.EXTENSION_ELEM_TYPE, paths)
            if (!resolveExtElem(source, id, key, paths, name,
              ivector(extName))) {
              val paths = H.paths(packageName, extName, expExtName)
              val key = H.symbolUri(H.EXTENSION_ELEM_TYPE, paths)
              resolveExtElem(source, id, key, paths, name,
                H.paths(packageName, extName))
            }
            false
          } else true
      }
    }
  }

  def resolveExtElem(source : Option[FileResourceUri],
                     extElemR : SymbolUser,
                     key : String, extElemPaths : ISeq[String]) : Boolean =
    tables.extensionElementTable.get(key) match {
      case Some(ee) =>
        H.symbolInit(extElemR, H.EXTENSION_ELEM_TYPE, extElemPaths, key)
        source.foreach { addDependency(_, ee.name) }
        true
      case _ =>
        false
    }

  def resolveExtElem(source : Option[FileResourceUri],
                     extElemR : SymbolUser,
                     key : String, extElemPaths : ISeq[String],
                     extR : SymbolUser,
                     extPaths : => ISeq[String]) : Boolean =
    tables.extensionElementTable.get(key) match {
      case Some(ee) =>
        H.symbolInit(extR, H.EXTENSION_TYPE, extPaths)
        H.symbolInit(extElemR, H.EXTENSION_ELEM_TYPE, extElemPaths, key)
        source.foreach { addDependency(_, ee.name) }
        true
      case _ =>
        false
    }

  def resolveTypeExt(source : Option[FileResourceUri],
                     typeExtR : SymbolUser,
                     key : String,
                     typeExtPaths : ISeq[String]) : Boolean =
    tables.extensionElementTable.get(key) match {
      case Some(te) =>
        H.symbolInit(typeExtR, H.TYPE_EXTENSION_TYPE, typeExtPaths, key)
        source.foreach { addDependency(_, te.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait FunResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val funResolver : VisitorFunction = {
    if (tables.funTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case ne : NameExp =>
          val funName = ne.name.name
          val paths = ivector(funName)
          val key = H.symbolUri(H.FUN_TYPE, paths)
          if (packageName != null) {
            if (!resolveFun(source, ne.name, key, paths)) {
              val paths = H.paths(packageName, funName)
              val key = H.symbolUri(H.FUN_TYPE, paths)
              resolveFun(source, ne.name, key, paths)
            }
          } else {
            resolveFun(source, ne.name, key, paths)
          }
          false
      }
    }
  }

  def resolveFun(source : Option[FileResourceUri],
                 funR : SymbolUser,
                 key : String,
                 funPaths : ISeq[String]) : Boolean =
    tables.funTable.get(key) match {
      case Some(fd) =>
        H.symbolInit(funR, H.FUN_TYPE, funPaths, key)
        source.foreach { addDependency(_, fd.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait GlobalVarResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  def globalVarResolver : VisitorFunction = {
    if (tables.globalVarTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case ne : NameExp =>
          val globalVarName = ne.name.name
          val paths = ivector(globalVarName)
          val key = H.symbolUri(H.GLOBAL_VAR_TYPE, paths)
          if (packageName != null) {
            if (!resolveGlobalVar(source, ne.name, key, paths)) {
              val paths = H.paths(packageName, globalVarName)
              val key = H.symbolUri(H.GLOBAL_VAR_TYPE, paths)
              resolveGlobalVar(source, ne.name, key, paths)
            }
          } else {
            resolveGlobalVar(source, ne.name, key, paths)
          }
          false
      }
    }
  }

  def resolveGlobalVar(source : Option[FileResourceUri],
                       globalVarR : SymbolUser,
                       key : String,
                       globalVarPaths : ISeq[String]) : Boolean =
    tables.globalVarTable.get(key) match {
      case Some(gvd) =>
        H.symbolInit(globalVarR, H.GLOBAL_VAR_TYPE, globalVarPaths, key)
        source.foreach { addDependency(_, gvd.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait ProcedureResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val procedureResolver : VisitorFunction = {
    if (tables.procedureTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case ne : NameExp =>
          val procedureName = ne.name
          val paths = ivector(procedureName.name)
          val key = H.symbolUri(H.PROCEDURE_TYPE, paths)
          if (packageName != null) {
            if (!resolveProcedure(source, procedureName, key, paths)) {
              val paths = H.paths(packageName, procedureName.name)
              val key = H.symbolUri(H.PROCEDURE_TYPE, paths)
              resolveProcedure(source, procedureName, key, paths)
            }
          } else {
            resolveProcedure(source, procedureName, key, paths)
          }
          false
      }
    }
  }

  def resolveProcedure(source : Option[FileResourceUri],
                       procedureR : SymbolUser,
                       key : String,
                       procedurePaths : ISeq[String]) : Boolean =
    tables.procedureTable.get(key) match {
      case Some(pds) =>
        H.symbolInit(procedureR, H.PROCEDURE_TYPE, procedurePaths, key)
        source.foreach { addDependency(_, tables.procedureAbsTable(pds(0)).name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait RecordHierarchyResolver extends SymbolResolver {

  def locPropKey : Property.Key

  def recordHierarchyResolver(stp : SymbolTableProducer) : Unit =
    if (!stp.tables.recordTable.isEmpty)
      for (rd <- stp.tables.recordTable.values)
        rd.extendsClauses.foreach { ec =>
          import LineColumnLocation._
          import FileLocation._
          val nameUser = ec.name
          val recordName = nameUser.name
          val source =
            if (rd.name ? locPropKey)
              rd.name.fileUriOpt
            else None
          val paths = ivector(recordName)
          val key = H.symbolUri(H.RECORD_TYPE, paths)
          var success = resolveRecordHierarchy(stp, source, nameUser, key, paths)
          if (!success) {
            val paths = ivector(H.packageName(rd.name), recordName)
            val key = H.symbolUri(H.RECORD_TYPE, paths)
            success = resolveRecordHierarchy(stp, source, nameUser, key, paths)
          }
          if (!success)
            stp.reportError(locPropKey, source, nameUser,
              NOT_FOUND_EXTEND_RECORD.format(nameUser.name))
        }

  def resolveRecordHierarchy(stp : SymbolTableProducer,
                             source : Option[FileResourceUri],
                             recordR : SymbolUser,
                             key : String,
                             recordPaths : ISeq[String]) : Boolean =
    stp.tables.recordTable.get(key) match {
      case Some(rd) =>
        H.symbolInit(recordR, H.RECORD_TYPE, recordPaths, key)
        source.foreach { addDependency(_, rd.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait RecordResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  def locPropKey : Property.Key

  val recordResolver : VisitorFunction = {
    if (tables.recordTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case rd : RecordDecl =>
          if (!rd.typeVars.isEmpty &&
            (!rd.attributes.isEmpty ||
              rd.extendsClauses.exists { !_.typeArgs.isEmpty })) {
            val tvt = mmapEmpty[String, NameDefinition]
            rd.typeVars.foreach { tv =>
              val nameDef = tv._1
              tvt(H.symbolSimpleName(nameDef)) = nameDef
            }
            val visitor = Visitor.build({
              case tvs : TypeVarSpec =>
                H.resolveTypeVar(source, self, tvs.name,
                  tvt, "record '%s'".format(rd.name.name),
                  locPropKey, true)
                false
              case nts : NamedTypeSpec =>
                H.resolveTypeVar(source, self, nts.name,
                  tvt, "record '%s'".format(rd.name.name),
                  locPropKey, false)
                false
            })
            rd.attributes.foreach { a => visitor(a.typeSpec) }
            rd.extendsClauses.foreach { ec =>
              ec.typeArgs.foreach { visitor(_) }
            }
          }
          true
        case NamedTypeSpec(id, _) =>
          val recordName = id.name
          val paths = ivector(recordName)
          val key = H.symbolUri(H.RECORD_TYPE, paths)
          if (packageName != null) {
            if (!resolveRecord(source, id, key, paths)) {
              val paths = H.paths(packageName, recordName)
              val key = H.symbolUri(H.RECORD_TYPE, paths)
              resolveRecord(source, id, key, paths)
            }
          } else {
            resolveRecord(source, id, key, paths)
          }
          false
      }
    }
  }

  def attributeInitResolver : VisitorFunction = {
    var source : Option[FileResourceUri] = None
    return {
      case m : Model =>
        source = m.sourceURI
        true
      case nr : NewRecordExp =>
        val rNameUser = nr.recordType.name
        nr.attributeInits.foreach { ai =>
          val paths = rNameUser.uriPaths :+ ai.name.name
          val key = H.symbolUri(H.RECORD_TYPE, paths)
          resolveAttribute(source, ai.name, key, paths)
        }
        true
    }
  }

  def resolveAttribute(source : Option[FileResourceUri],
                       attrR : NameUser,
                       key : String,
                       attrPaths : ISeq[String]) =
    tables.attributeTable.get(key) match {
      case Some(ad) =>
        H.symbolInit(attrR, H.ATTRIBUTE_TYPE, attrPaths, key)
        source.foreach { addDependency(_, ad.name) }
        true
      case _ =>
        false
    }

  def resolveRecord(source : Option[FileResourceUri],
                    recordR : SymbolUser,
                    key : String,
                    recordPaths : ISeq[String]) : Boolean =
    tables.recordTable.get(key) match {
      case Some(rd) =>
        H.symbolInit(recordR, H.RECORD_TYPE, recordPaths, key)
        source.foreach { addDependency(_, rd.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait TypeAliasResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val typeAliasResolver : VisitorFunction = {
    if (!tables.typeAliasTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case nts : NamedTypeSpec =>
          val typeAliasName = nts.name.name
          val paths = ivector(typeAliasName)
          val key = H.symbolUri(H.TYPE_ALIAS_TYPE, paths)
          if (packageName != null) {
            if (!resolveTypeAlias(source, nts.name, key, paths)) {
              val paths = H.paths(packageName, typeAliasName)
              val key = H.symbolUri(H.TYPE_ALIAS_TYPE, paths)
              resolveTypeAlias(source, nts.name, key, paths)
            }
          } else {
            resolveTypeAlias(source, nts.name, key, paths)
          }
          false
      }
    }
  }

  def resolveTypeAlias(source : Option[FileResourceUri],
                       typeAliasR : SymbolUser,
                       key : String,
                       typeAliasPaths : ISeq[String]) : Boolean =
    tables.typeAliasTable.get(key) match {
      case Some(tad) =>
        H.symbolInit(typeAliasR, H.TYPE_ALIAS_TYPE, typeAliasPaths, key)
        source.foreach { addDependency(_, tad.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait VsetResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  val vsetResolver : VisitorFunction = {
    if (tables.vsetTable.isEmpty)
      ignoringVisitorFunction
    else {
      var source : Option[FileResourceUri] = None
      var packageName : Option[NameDefinition] = None;

      {
        case m : Model =>
          source = m.sourceURI
          true
        case pd : PackageDecl =>
          packageName = pd.name
          true
        case ne : NameExp =>
          if (source != null && packageName != null) {
            val vsetName = ne.name.name
            val paths = ivector(vsetName)
            val key = H.symbolUri(H.VSET_TYPE, paths)
            if (!resolveVset(source, ne.name, key, paths)) {
              val paths = H.paths(packageName, vsetName)
              val key = H.symbolUri(H.VSET_TYPE, paths)
              resolveVset(source, ne.name, key, paths)
            }
          }
          false
      }
    }
  }

  def resolveVset(source : Option[FileResourceUri],
                  vsetR : SymbolUser,
                  key : String,
                  vsetPaths : ISeq[String]) : Boolean =
    tables.vsetTable.get(key) match {
      case Some(vd) =>
        H.symbolInit(vsetR, H.VSET_TYPE, vsetPaths, key)
        source.foreach { addDependency(_, vd.name) }
        true
      case _ =>
        false
    }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait FunParamResolver extends SymbolResolver {
  self : SymbolTableProducer =>

  def locPropKey : Property.Key

  val scopeStack = mlistEmpty[MMap[String, ParamDecl]]
  val funParamResolver : VisitorFunction = {
    case m : Matching =>
      val scope = mmapEmpty[String, ParamDecl]
      val visitor = Visitor.build {
        case p : ParamDecl =>
          val pName = p.name
          H.symbolInit(pName, H.ANON_LOCAL_TYPE, ivector(pName.name))
          val key = pName.uri
          scope.get(key) match {
            case Some(other) =>
              reportRedeclaration(locPropKey, pName, DUPLICATE_ANON_PARAM,
                other)
            case _ =>
              scope(key) = p
          }
          false
      }
      m.params.foreach { visitor(_) }
      scopeStack.+=:(scope)
      true
    case ne : NameExp =>
      val key = H.symbolUri(H.ANON_LOCAL_TYPE, ivector(ne.name.name))
      var found = false
      var i = 0
      scopeStack.foreach { scope =>
        if (!found) {
          scope.get(key) match {
            case Some(sp) =>
              val spName = sp.name
              H.symbolInit(ne.name, H.ANON_LOCAL_TYPE,
                spName.uriPaths :+ i.toString)
              found = true
            case _ =>
              i += 1
          }
        }
      }
      true
  }

  val funParamResolverEnd : VisitorFunction = {
    case m : Matching => scopeStack.remove(0); true
  }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait ProcedureSymbolResolver extends SymbolResolver {
  self : ProcedureSymbolTableProducer =>

  def locPropKey : Property.Key

  val procedureHeaderResolver : VisitorFunction = {
    case pd : ProcedureDecl =>
      if (!pd.typeVars.isEmpty) {
        val tvt = H.buildTypeVarMap(pd.typeVars)
        import FileLocation._
        val source =
          if (pd.name ? locPropKey)
            pd.name.fileUriOpt
          else None
        Visitor.build {
          case NamedTypeSpec(id, Seq()) =>
            H.resolveTypeVar(source, self.symbolTableProducer, id, tvt,
              "procedure '%s'".format(pd.name.name), locPropKey, false)
            false
          case tvs : TypeVarSpec =>
            H.resolveTypeVar(source, self.symbolTableProducer, tvs.name, tvt,
              "procedure '%s'".format(pd.name.name), locPropKey, true)
            false
          case body : Body =>
            false
        }(pd)
      }
      false
  }

  def procedureBodyResolver : VisitorFunction = {
    var source : Option[FileResourceUri] = None
    var tvt : MMap[String, NameDefinition] = null
    var procNameDef : NameDefinition = null
    return {
      case pd : ProcedureDecl =>
        procNameDef = pd.name
        import FileLocation._
        if (procNameDef ? locPropKey)
          source = procNameDef.fileUriOpt
        tvt = H.buildTypeVarMap(pd.typeVars)
        true
      case ne : NameExp =>
        val paths = procNameDef.uriPaths :+ ne.name.name
        val key = H.symbolUri(H.LOCAL_VAR_TYPE, paths)
        tables.localVarTable.get(key) match {
          case Some(lv) =>
            H.symbolInit(ne.name, H.LOCAL_VAR_TYPE, paths,
              lv.name.uri)
          case _ =>
        }
        false
      case NamedTypeSpec(id, Seq()) =>
        H.resolveTypeVar(source, self.symbolTableProducer, id, tvt,
          "procedure '%s'".format(procNameDef.name), locPropKey, false)
        false
      case tvs : TypeVarSpec =>
        H.resolveTypeVar(source, self.symbolTableProducer, tvs.name, tvt,
          "procedure '%s'".format(procNameDef.name), locPropKey, true)
        false
    }
  }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait JumpResolver extends SymbolResolver {
  self : ProcedureSymbolTableProducer =>

  def locPropKey : Property.Key

  def hasImplicitNextJump(loc : LocationDecl) : Boolean = {
      def hasImplicit(j : Jump) : Boolean =
        j match {
          case j : IfJump if j.ifElse.isEmpty          => true
          case j : SwitchJump if j.defaultCase.isEmpty => true
          case j : CallJump =>
            if (j.jump.isEmpty) true
            else hasImplicit(j.jump.get)
          case _ => false
        }
    loc match {
      case loc : ActionLocation =>
        !loc.action.isInstanceOf[ThrowAction]
      case loc : EmptyLocation => true
      case loc : JumpLocation  => hasImplicit(loc.jump)
      case loc : ComplexLocation =>
        loc.transformations.exists { t =>
          if (t.actions.exists(_.isInstanceOf[ThrowAction])) false
          else if (t.jump.isEmpty) true
          else hasImplicit(t.jump.get)
        }
      case _ => false
    }
  }

  val jumpResolver : VisitorFunction = {
    var source : Option[FileResourceUri] = None
    var procNameDef : NameDefinition = null
    var locations : ISeq[LocationDecl] = null;
    import LineColumnLocation._
    import FileLocation._
    {
      case pd : ProcedureDecl =>
        procNameDef = pd.name
        if (procNameDef ? locPropKey)
          source = procNameDef.fileUriOpt
        true
      case ib : ImplementedBody =>
        locations = ib.locations
        val lastLoc = locations(locations.size - 1)
        if (hasImplicitNextJump(lastLoc)) {
          lastLoc.name match {
            case Some(name) =>
              symbolTableProducer.reportError(locPropKey, name,
                LAST_LOCATION_NEED_EXPLICIT_JUMP)
            case _ =>
              symbolTableProducer.reportError(locPropKey, lastLoc,
                LAST_LOCATION_NEED_EXPLICIT_JUMP)
          }
        }
        true
      case gj : GotoJump =>
        val nameUser = gj.target
        location(source, nameUser, procNameDef)
        false
      case iftj : IfThenJump =>
        val nameUser = iftj.target
        location(source, nameUser, procNameDef)
        false
      case scj : SwitchCaseJump =>
        val nameUser = scj.target
        location(source, nameUser, procNameDef)
        false
      case cc : CatchClause =>
        val fromNameUser = cc.fromTarget
        location(source, fromNameUser, procNameDef)
        val toNameUser = cc.toTarget
        location(source, toNameUser, procNameDef)
        val start = tables.bodyTables.get.locationTable(fromNameUser.uri).index
        val end = tables.bodyTables.get.locationTable(toNameUser.uri).index
        if (end == -1) {
          if (fromNameUser ? locPropKey)
            symbolTableProducer.reportError(locPropKey, source, toNameUser,
              CATCH_TABLE_END_BEFORE_START.
                format(H.symbolSimpleName(toNameUser),
                  H.symbolSimpleName(fromNameUser),
                  fromNameUser.line, fromNameUser.column))
          else
            symbolTableProducer.reportError(locPropKey, source, toNameUser,
              CATCH_TABLE_END_BEFORE_START.
                format(H.symbolSimpleName(toNameUser),
                  H.symbolSimpleName(fromNameUser),
                  0, 0))
        } else {
          val ct = tables.bodyTables.get.catchTable
          for (i <- start to end)
            ct.getOrElseUpdate(locations(i).index, marrayEmpty) += cc
        }
        true
      case lvd : LocalVarDecl =>
        false
      case a : Annotation =>
        false
      case g : Guard =>
        false
      case g : Exp =>
        false
    }
  }

  def location(source : Option[FileResourceUri],
               nameUser : NameUser,
               procNameDef : NameDefinition) = {
    val key = H.symbolUri(H.LOCATION_TYPE, ivector(nameUser.name), true)
    tables.bodyTables.get.locationTable.get(key) match {
      case Some(ld) =>
        val symDef = ld.name.get
        H.symbolInit(nameUser, symDef.uriType,
          symDef.uriPaths, symDef.uri)
      case None =>
        symbolTableProducer.reportError(locPropKey, source, nameUser,
          NOT_FOUND_LOCATION.format(nameUser.name, procNameDef.name))
    }
  }
}
