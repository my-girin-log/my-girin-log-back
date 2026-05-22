package com.mygrinlog.persona;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonaService {

    private final PersonaRepository personaRepository;

    public PersonaService(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    /** Persona 가 있으면 갱신, 없으면 생성. 호출자(컨트롤러)는 트랜잭션 밖에서 LLM 호출 후 이 메서드만 호출. */
    @Transactional
    public Persona upsert(Long userId, PersonaDraft draft) {
        Persona persona = personaRepository.findByUserId(userId).orElse(null);
        if (persona == null) {
            persona = new Persona(userId, draft.personaMd(), draft.summary(), draft.sources(), draft.analysis());
        } else {
            persona.update(draft.personaMd(), draft.summary(), draft.sources(), draft.analysis());
        }
        return personaRepository.save(persona);
    }
}
