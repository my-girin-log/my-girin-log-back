package com.mygrinlog.pet;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wootegotchi")
public record PetProperties(Exp exp, Level level, ConditionDays condition, PetImage petImage) {

    public record Exp(int perMessage, int perDiary, int perRetrospective) {}

    public record Level(int cap, int expPerLevel) {}

    public record ConditionDays(long goodMaxDays, long badMaxDays) {}

    public record PetImage(String cdnBase, int frames) {}
}
