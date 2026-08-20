FROM debian:bookworm-slim
COPY ollama /usr/local/bin/ollama
RUN chmod +x /usr/local/bin/ollama
EXPOSE 11434
ENTRYPOINT ["/usr/local/bin/ollama"]
CMD ["serve"]