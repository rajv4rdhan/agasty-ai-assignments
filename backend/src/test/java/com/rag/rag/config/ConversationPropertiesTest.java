package com.rag.rag.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationPropertiesTest {
    
    @Test
    void shouldHaveDefaultValues() {
        ConversationProperties props = new ConversationProperties();
        assertThat(props.getMaxTurns()).isEqualTo(10);
        assertThat(props.getMaxTokens()).isEqualTo(4000);
    }
    
    @Test
    void shouldAllowSettersToChangeValues() {
        ConversationProperties props = new ConversationProperties();
        
        props.setMaxTurns(20);
        props.setMaxTokens(8000);
        
        assertThat(props.getMaxTurns()).isEqualTo(20);
        assertThat(props.getMaxTokens()).isEqualTo(8000);
    }
}
