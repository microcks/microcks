import com.eviware.soapui.support.XmlHolder
/**
 * Script permettant de valider les valeurs situé dans le CDATA
 */
def holder = new XmlHolder( mockRequest.requestContent )

// On récupère le contenu de la balise <DET> qui est la racine de la question
def node = holder["//DET[1]"]

// On parse la chaine de caractères CDATA
def descHolder = new XmlHolder( node )

// On récupère la valeur de la balise <Cd>
def cdEnergie = descHolder["//DET-QUE[1]/CdEngy[1]"]
def puissAdmin = descHolder["//DET-QUE[1]/PuissAdmin[1]"]
def dateMiseEnService = descHolder["//DET-QUE[1]/DtMec[1]"]
 
if(cdEnergie && puissAdmin && dateMiseEnService){
    return "RechercherVehicule_peugeot_206_essence_puissfisc4_dt20140214 Response"
}else if(cdEnergie && puissAdmin ){
    return "RechercherVehicule_peugeot_206_essence_puissfisc4 Response"
}else if (cdEnergie){
    return "RechercherVehicule_peugeot_206_essence Response"
}else{
    return "RechercherVehicule_peugeot_206 Response"
}