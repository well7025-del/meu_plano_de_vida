import { registerRootComponent } from 'expo';
import App from './App';
// Importado por efeito colateral: registra a tarefa de segundo plano antes de
// qualquer tela montar. Se isso vier depois, o SO pode acordar o app para uma
// atualizacao de localizacao e nao encontrar a tarefa definida.
import './src/tracking/locationTask';

registerRootComponent(App);
