import js from '@eslint/js';import globals from 'globals';import hooks from 'eslint-plugin-react-hooks';import refresh from 'eslint-plugin-react-refresh';import tseslint from 'typescript-eslint';
export default tseslint.config(
  {ignores:['dist','supabase']},
  {extends:[js.configs.recommended,...tseslint.configs.recommended],files:['**/*.{ts,tsx}'],languageOptions:{ecmaVersion:2020,globals:globals.browser},plugins:{'react-hooks':hooks,'react-refresh':refresh},rules:{...hooks.configs.recommended.rules,'react-refresh/only-export-components':['warn',{allowConstantExport:true}]}},
  {files:['src/live/**/*.{ts,tsx}'],rules:{'@typescript-eslint/no-explicit-any':'off','@typescript-eslint/no-unused-vars':'warn'}}
)
