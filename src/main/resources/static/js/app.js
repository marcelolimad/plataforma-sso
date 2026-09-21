document.querySelectorAll('textarea[maxlength]').forEach(field => {
  const counter = document.createElement('small');
  counter.className = 'char-counter';
  field.insertAdjacentElement('afterend', counter);
  const refresh = () => { counter.textContent = `${field.value.length}/${field.maxLength} caracteres`; };
  field.addEventListener('input', refresh);
  refresh();
});
