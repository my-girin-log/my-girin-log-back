package com.mygrinlog.persona;

import com.mygrinlog.common.jpa.JsonObjectConverter;
import jakarta.persistence.Converter;

@Converter
public class PersonaAnalysisConverter extends JsonObjectConverter<PersonaAnalysis> {

    @Override
    protected Class<PersonaAnalysis> targetType() {
        return PersonaAnalysis.class;
    }
}
