package com.mygrinlog.user;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.common.web.NotFoundException;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaDraft;
import com.mygrinlog.persona.PersonaGenerator;
import com.mygrinlog.persona.PersonaRepository;
import com.mygrinlog.persona.PersonaService;
import com.mygrinlog.pet.PetState;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.pet.PetView;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 워넬 자료 §2.1.
 *  - GET  /users/me            : user 블록 + pet 블록 (PetView).
 *                                 ?level=&condition= 으로 펫 응답만 강제 변경 (DB 미변경, 데모/모킹용).
 *  - POST /users/onboarding    : sources/rawText → PersonaGenerator(트랜잭션 밖) → upsert Persona.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/users")
public class UserController {

    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final PersonaGenerator personaGenerator;
    private final PersonaService personaService;
    private final PetStateService petStateService;

    public UserController(UserRepository userRepository,
                          PersonaRepository personaRepository,
                          PersonaGenerator personaGenerator,
                          PersonaService personaService,
                          PetStateService petStateService) {
        this.userRepository = userRepository;
        this.personaRepository = personaRepository;
        this.personaGenerator = personaGenerator;
        this.personaService = personaService;
        this.petStateService = petStateService;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public MeResponse me(@CurrentUser Long userId,
                         @RequestParam(required = false) Integer level,
                         @RequestParam(required = false) PetState.Condition condition) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user not found: " + userId));
        boolean hasPersona = personaRepository.findByUserId(userId).isPresent();
        UserView userView = new UserView(user.getId(), user.getGithubId(), user.getNickname(),
                user.getAvatarUrl(), hasPersona, user.getCreatedAt());
        PetView petView = petStateService.view(userId, level, condition);
        return new MeResponse(userView, petView);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardingResponse onboarding(@CurrentUser Long userId,
                                         @RequestBody OnboardingRequest request) {
        if (request == null || ((request.sources() == null || request.sources().isEmpty())
                && (request.rawText() == null || request.rawText().isBlank()))) {
            throw new IllegalArgumentException("sources 또는 rawText 중 하나는 필수");
        }
        // 프론트에서 닉네임도 같이 보내면 user.nickname 도 갱신 (프론트 호환)
        if (request.nickname() != null && !request.nickname().isBlank()) {
            userRepository.findById(userId).ifPresent(u -> u.updateProfile(request.nickname().trim(), u.getAvatarUrl()));
        }
        // ⚠ Persona 생성기 호출은 트랜잭션 밖 (스펙 §5)
        PersonaDraft draft = personaGenerator.generate(
                request.sources() == null ? List.of() : request.sources(),
                request.rawText()
        );
        // 저장은 PersonaService 의 @Transactional 메서드 (별도 bean 호출이라 프록시 통과)
        Persona saved = personaService.upsert(userId, draft);
        return new OnboardingResponse(saved.getId(), saved.getSummary(), saved.getMarkdown());
    }

    public record OnboardingRequest(List<String> sources, String rawText, String nickname) {}

    public record OnboardingResponse(Long personaId, String summary, String markdown) {}

    public record MeResponse(UserView user, PetView pet) {}

    public record UserView(Long id, String githubId, String nickname, String avatarUrl,
                           boolean hasPersona, Instant createdAt) {}
}
