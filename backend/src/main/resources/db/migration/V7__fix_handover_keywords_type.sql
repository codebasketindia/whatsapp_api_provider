-- Fix handover_keywords column type from LONGTEXT to JSON
ALTER TABLE ai_assistants MODIFY COLUMN handover_keywords JSON NULL;
