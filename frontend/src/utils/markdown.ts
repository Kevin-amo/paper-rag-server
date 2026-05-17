import DOMPurify from 'dompurify';
import MarkdownIt from 'markdown-it';

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
});

const defaultLinkOpenRenderer = markdown.renderer.rules.link_open;

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx];
  const targetIndex = token.attrIndex('target');
  const relIndex = token.attrIndex('rel');

  if (targetIndex < 0) {
    token.attrPush(['target', '_blank']);
  } else {
    token.attrs![targetIndex][1] = '_blank';
  }

  if (relIndex < 0) {
    token.attrPush(['rel', 'noopener noreferrer nofollow']);
  } else {
    token.attrs![relIndex][1] = 'noopener noreferrer nofollow';
  }

  return defaultLinkOpenRenderer
    ? defaultLinkOpenRenderer(tokens, idx, options, env, self)
    : self.renderToken(tokens, idx, options);
};

export function renderMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content || ''));
}