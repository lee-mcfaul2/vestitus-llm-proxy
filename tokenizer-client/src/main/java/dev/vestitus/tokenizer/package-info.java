/**
 * Pluggable client for the external PII Tokenizer service. vestitus owns no
 * tokenization key, logic, or guarantee; it brackets a per-request session and
 * asks the tokenizer to tokenize/detokenize within it. See the design spec
 * dated 2026-05-19.
 */
package dev.vestitus.tokenizer;
