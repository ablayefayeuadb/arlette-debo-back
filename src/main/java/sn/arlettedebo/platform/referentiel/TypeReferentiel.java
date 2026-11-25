package sn.arlettedebo.platform.referentiel;

import sn.arlettedebo.platform.referentiel.internal.CanalPublication;
import sn.arlettedebo.platform.referentiel.internal.CategorieArticle;
import sn.arlettedebo.platform.referentiel.internal.CategorieDepense;
import sn.arlettedebo.platform.referentiel.internal.ChecklistItem;
import sn.arlettedebo.platform.referentiel.internal.Formation;
import sn.arlettedebo.platform.referentiel.internal.Formule;
import sn.arlettedebo.platform.referentiel.internal.ModePaiement;
import sn.arlettedebo.platform.referentiel.internal.OptionSuiviSoin;
import sn.arlettedebo.platform.referentiel.internal.Perimetre;
import sn.arlettedebo.platform.referentiel.internal.Prestation;
import sn.arlettedebo.platform.referentiel.internal.Referentiel;
import sn.arlettedebo.platform.referentiel.internal.RegleGestion;
import sn.arlettedebo.platform.referentiel.internal.TypeAbsence;
import sn.arlettedebo.platform.referentiel.internal.TypeDecision;
import sn.arlettedebo.platform.referentiel.internal.TypeSignalement;
import sn.arlettedebo.platform.referentiel.internal.TypeVariablePaie;

/** Catalogues de référence exposés aux autres modules (par code, sans exposer les entités). */
public enum TypeReferentiel {
    PERIMETRE("perimetres", Perimetre.class),
    PRESTATION("prestations", Prestation.class),
    FORMULE("formules", Formule.class),
    MODE_PAIEMENT("modesPaiement", ModePaiement.class),
    CATEGORIE_DEPENSE("categoriesDepense", CategorieDepense.class),
    TYPE_ABSENCE("typesAbsence", TypeAbsence.class),
    TYPE_VARIABLE_PAIE("typesVariablePaie", TypeVariablePaie.class),
    CATEGORIE_ARTICLE("categoriesArticle", CategorieArticle.class),
    TYPE_DECISION("typesDecision", TypeDecision.class),
    TYPE_SIGNALEMENT("typesSignalement", TypeSignalement.class),
    CANAL_PUBLICATION("canauxPublication", CanalPublication.class),
    FORMATION("formations", Formation.class),
    CHECKLIST_ITEM("checklistHygiene", ChecklistItem.class),
    REGLE_GESTION("reglesGestion", RegleGestion.class),
    OPTION_SUIVI_SOIN("optionsSuiviSoin", OptionSuiviSoin.class);

    private final String cle;
    private final Class<? extends Referentiel> entite;

    TypeReferentiel(String cle, Class<? extends Referentiel> entite) {
        this.cle = cle;
        this.entite = entite;
    }

    public String cle() {
        return cle;
    }

    Class<? extends Referentiel> entite() {
        return entite;
    }
}
