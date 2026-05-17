permit(
  principal == User::"alice",
  action == Action::"read",
  resource == Resource::"mcp-a/files/contents"
) when { context.scopes.contains("read") };

forbid(
  principal,
  action == Action::"read",
  resource
) when { context.resource_tags has "pii" && context.resource_tags.pii == "true" };
