# conector-core

API REST (Quarkus) que llama programas del AS400 usando PCML. Un solo endpoint, `POST /programa`, sirve para
cualquier programa: para exponer uno nuevo solo hay que compilarlo en el AS400 y agregar su `.pcml` al proyecto.

Esta guía es una receta: sigue los pasos en orden. En cada uno se indica **qué comando ejecutar**, **qué pegar** y
**cómo compilar y probar**.

Convenciones:

- **Comando AS400**: se escribe en la línea de comandos de la sesión 5250 (`===>`) y se pulsa Enter.
- **VS Code**: con la extensión *Code for IBM i* conectada a PUB400.
- **Terminal**: terminal de tu PC, en la carpeta del proyecto.
- Librería usada en todos los ejemplos: `SDSANTISH1`. Si usas otra, cámbiala en todos los comandos y en los `path`
  de los PCML.

---

## Índice

- [Paso 0 — Levantar el conector](#paso-0--levantar-el-conector)
- [Paso 1 — Crear los archivos fuente en el AS400](#paso-1--crear-los-archivos-fuente-en-el-as400)
- [Paso 2 — Programa SALUDO](#paso-2--programa-saludo)
- [Paso 3 — Programa SUMA](#paso-3--programa-suma)
- [Paso 4 — Programa PRUEBAPCML](#paso-4--programa-pruebapcml)
- [Paso 5 — Tabla CLIENTES](#paso-5--tabla-clientes)
- [Paso 6 — Programa CLIINS (guardar clientes)](#paso-6--programa-cliins-guardar-clientes)
- [Paso 7 — Programa CLICON (consultar clientes)](#paso-7--programa-clicon-consultar-clientes)
- [Paso 8 — Agregar cualquier otro programa](#paso-8--agregar-cualquier-otro-programa)
- [Si algo falla](#si-algo-falla)
- [Referencia](#referencia)

---

## Paso 0 — Levantar el conector

**0.1** Comprueba que tienes JDK 21 o superior. En la terminal:

```bash
./mvnw -v
```

La línea `Java version` debe decir 21 o más.

**0.2** Define la contraseña del AS400 (host `pub400.com` y usuario `sdsantish` ya vienen por defecto en
`application.properties`):

```bash
export AS400_PASSWORD='tu-contraseña'
```

**0.3** Levanta el conector:

```bash
./mvnw quarkus:dev
```

**0.4** Comprueba que responde (pide un PCML que no existe, así no toca el AS400):

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "noexiste" }'
```

Debe devolver `{"error":"No existe el PCML noexiste"}`. Deja esta terminal abierta; las pruebas de los pasos
siguientes se hacen contra `http://localhost:8080/programa`.

---

## Paso 1 — Crear los archivos fuente en el AS400

Se hace una sola vez. Son las "carpetas" donde se guardan los fuentes CL y RPG.

**1.1** Comando AS400:

```
CRTSRCPF FILE(SDSANTISH1/QCLSRC) RCDLEN(112) TEXT('Fuentes CL')
```

**1.2** Comando AS400:

```
CRTSRCPF FILE(SDSANTISH1/QRPGLESRC) RCDLEN(112) TEXT('Fuentes RPG')
```

Si alguno responde `CPF7302` / "already exists", ya lo tienes: continúa.

---

## Paso 2 — Programa SALUDO

Recibe un nombre y devuelve `HOLA <nombre>`.

**2.1 Crear el miembro.** Comando AS400:

```
ADDPFM FILE(SDSANTISH1/QCLSRC) MBR(SALUDO) SRCTYPE(CLLE) TEXT('Programa de saludo')
```

Si responde que el miembro ya existe, sigue al paso 2.2 y reemplaza todo su contenido.

**2.2 Abrir el miembro.** En VS Code → panel **IBM i** → **OBJECT BROWSER** → `SDSANTISH1` → `QCLSRC` →
`SALUDO.CLLE` (si no aparece, botón de refrescar).

**2.3 Pegar** este contenido (borra lo que hubiera) y guarda con `Ctrl+S`:

```
            PGM        PARM(&NAME &MSG)

            /* Parametro de entrada */
            DCL        VAR(&NAME) TYPE(*CHAR) LEN(20)

            /* Parametro de salida: lo devuelve al llamador */
            DCL        VAR(&MSG) TYPE(*CHAR) LEN(30)

            /* Concatenar 'HOLA ' con el nombre recibido */
            CHGVAR     VAR(&MSG) VALUE('HOLA ' *CAT &NAME)

            ENDPGM
```

**2.4 Compilar.** Comando AS400:

```
CRTBNDCL PGM(SDSANTISH1/SALUDO) SRCFILE(SDSANTISH1/QCLSRC)
```

Resultado esperado: `Program SALUDO created in library SDSANTISH1`.

**2.5 PCML.** Ya existe en el proyecto: `src/main/resources/pcml/saludo.pcml`. Su contenido es:

```xml
<pcml version="4.0">
   <program name="saludo" path="/QSYS.LIB/SDSANTISH1.LIB/SALUDO.PGM">
      <data name="name" type="char" length="20" usage="input"/>
      <data name="msg"  type="char" length="30" usage="output"/>
   </program>
</pcml>
```

**2.6 Probar.** Terminal (o importar en Postman):

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "saludo", "parametros": { "name": "CARLOS" } }'
```

Respuesta esperada:

```json
{ "pcml": "saludo", "programa": "saludo", "resultado": { "msg": "HOLA CARLOS" } }
```

---

## Paso 3 — Programa SUMA

Suma dos números con hasta 10 enteros y 5 decimales.

**3.1 Crear el miembro.** Comando AS400:

```
ADDPFM FILE(SDSANTISH1/QCLSRC) MBR(SUMA) SRCTYPE(CLLE) TEXT('Programa de suma de 2 numeros')
```

**3.2 Abrir el miembro.** VS Code → **OBJECT BROWSER** → `SDSANTISH1` → `QCLSRC` → `SUMA.CLLE`.

**3.3 Pegar** y guardar con `Ctrl+S`:

```
            PGM        PARM(&NUM1 &NUM2 &RESULT)

            /* Parametros de entrada */
            DCL        VAR(&NUM1) TYPE(*DEC) LEN(15 5)
            DCL        VAR(&NUM2) TYPE(*DEC) LEN(15 5)

            /* Parametro de salida: lo devuelve al llamador */
            DCL        VAR(&RESULT) TYPE(*DEC) LEN(15 5)

            CHGVAR     VAR(&RESULT) VALUE(&NUM1 + &NUM2)

            ENDPGM
```

**3.4 Compilar.** Comando AS400:

```
CRTBNDCL PGM(SDSANTISH1/SUMA) SRCFILE(SDSANTISH1/QCLSRC)
```

**3.5 PCML.** Ya existe: `src/main/resources/pcml/suma.pcml`.

```xml
<pcml version="4.0">
   <program name="suma" path="/QSYS.LIB/SDSANTISH1.LIB/SUMA.PGM">
      <data name="num1"   type="packed" length="15" precision="5" usage="input"/>
      <data name="num2"   type="packed" length="15" precision="5" usage="input"/>
      <data name="result" type="packed" length="15" precision="5" usage="output"/>
   </program>
</pcml>
```

**3.6 Probar:**

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "suma", "parametros": { "num1": 10.5, "num2": 4.25 } }'
```

Respuesta esperada:

```json
{ "pcml": "suma", "programa": "suma", "resultado": { "result": 14.75000 } }
```

---

## Paso 4 — Programa PRUEBAPCML

Recibe un carrito (cliente + hasta 5 productos) y devuelve el total de unidades. Ejemplo de estructuras y listas.
No guarda nada en la base de datos.

**4.1 Crear el miembro.** Comando AS400:

```
ADDPFM FILE(SDSANTISH1/QRPGLESRC) MBR(PRUEBAPCML) SRCTYPE(RPGLE) TEXT('Prueba de estructuras complejas')
```

**4.2 Abrir el miembro.** VS Code → **OBJECT BROWSER** → `SDSANTISH1` → `QRPGLESRC` → `PRUEBAPCML.RPGLE`.

**4.3 Pegar** y guardar con `Ctrl+S`. **`**free` debe quedar en la columna 1, sin espacios delante.**

```
**free
// ------------------------------------------------------------------
// PRUEBAPCML: suma las cantidades de los productos de un carrito.
// El PCML se genera al compilar con PGMINFO(*PCML) INFOSTMF(...).
// ------------------------------------------------------------------

// 1. Estructuras de entrada
dcl-ds producto_t qualified template;
  idProducto int(10);
  cantidad   int(10);
end-ds;

dcl-ds bodyInput_t qualified template;
  idCliente  int(10);
  nombre     char(20);
  totalItems int(10);                   // Productos enviados (max. 5)
  listaProd  likeds(producto_t) dim(5); // Siempre ocupa 5 posiciones
end-ds;

// 2. Estructura de salida
dcl-ds respuestaOutput_t qualified template;
  status       char(10);
  msg          char(30);
  totalCarrito int(10);
end-ds;

// 3. Parametros del programa
dcl-pi *n;
  pInput  likeds(bodyInput_t);
  pOutput likeds(respuestaOutput_t);
end-pi;

// --- Logica principal ---
dcl-s i int(10);

pOutput.totalCarrito = 0;

// Evita leer fuera del arreglo si totalItems no esta entre 0 y 5
if pInput.totalItems < 0 or pInput.totalItems > %elem(pInput.listaProd);
  pOutput.status = 'ERROR';
  pOutput.msg = 'totalItems debe estar entre 0 y 5';
  *inlr = *on;
  return;
endif;

for i = 1 to pInput.totalItems;
  pOutput.totalCarrito += pInput.listaProd(i).cantidad;
endfor;

pOutput.status = 'SUCCESS';
pOutput.msg = 'Carrito procesado con exito';

*inlr = *on;
return;
```

**4.4 Compilar.** Comando AS400 (además del programa, genera su PCML en el IFS):

```
CRTBNDRPG PGM(SDSANTISH1/PRUEBAPCML) SRCFILE(SDSANTISH1/QRPGLESRC) PGMINFO(*PCML) INFOSTMF('/tmp/pruebapcml.pcml')
```

Resultado esperado: `Program PRUEBAPCML placed in library SDSANTISH1. 00 highest severity`.

**4.5 PCML.** Ya existe: `src/main/resources/pcml/pruebapcml.pcml` (es el que generó el compilador). Si
vuelves a compilar y quieres actualizarlo: VS Code → **IFS BROWSER** → `/tmp` → clic derecho en `pruebapcml.pcml` →
**Download** → guárdalo en `src/main/resources/pcml/`.

```xml
<pcml version="7.0">
   <struct name="RESPUESTAOUTPUT_T">
      <data name="STATUS" type="char" length="10" usage="inherit" />
      <data name="MSG" type="char" length="30" usage="inherit" />
      <data name="TOTALCARRITO" type="int" length="4" precision="31" usage="inherit" />
   </struct>
   <struct name="PRODUCTO_T">
      <data name="IDPRODUCTO" type="int" length="4" precision="31" usage="inherit" />
      <data name="CANTIDAD" type="int" length="4" precision="31" usage="inherit" />
   </struct>
   <struct name="BODYINPUT_T">
      <data name="IDCLIENTE" type="int" length="4" precision="31" usage="inherit" />
      <data name="NOMBRE" type="char" length="20" usage="inherit" />
      <data name="TOTALITEMS" type="int" length="4" precision="31" usage="inherit" />
      <data name="LISTAPROD" type="struct" struct="PRODUCTO_T" count="5" usage="inherit" />
   </struct>
   <program name="PRUEBAPCML" path="/QSYS.LIB/SDSANTISH1.LIB/PRUEBAPCML.PGM">
      <data name="PINPUT" type="struct" struct="BODYINPUT_T" usage="inputoutput" />
      <data name="POUTPUT" type="struct" struct="RESPUESTAOUTPUT_T" usage="inputoutput" />
   </program>
</pcml>
```

**4.6 Probar:**

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{
  "pcml": "pruebapcml",
  "parametros": {
    "pInput": {
      "idCliente": 1,
      "nombre": "CARLOS",
      "totalItems": 2,
      "listaProd": [
        { "idProducto": 10, "cantidad": 3 },
        { "idProducto": 20, "cantidad": 4 }
      ]
    }
  }
}'
```

En la respuesta, el resultado del programa está en `POUTPUT`:

```json
"POUTPUT": { "STATUS": "SUCCESS", "MSG": "Carrito procesado con exito", "TOTALCARRITO": 7 }
```

`PINPUT` también vuelve porque el PCML generado marca todo como `inputoutput`: es el eco de lo enviado, con los
productos que faltaban hasta 5 rellenos con ceros. Para que no vuelva, en el PCML cambia `usage="inputoutput"` por
`usage="input"` en `PINPUT` y por `usage="output"` en `POUTPUT`.

---

## Paso 5 — Tabla CLIENTES

Tabla donde los pasos 6 y 7 guardan y leen datos. El `ID` y la `FECHA_ALTA` los genera la tabla.

> La sentencia es demasiado larga para la línea de comandos del menú principal (se corta y da
> "A matching apostrophe not found"). Por eso se ejecuta dentro de `STRSQL`, que tiene un área de entrada grande.

**5.1 Abrir SQL interactivo.** Comando AS400:

```
STRSQL
```

**5.2 Crear la tabla.** Cada renglón de STRSQL es un campo de ~75 caracteres y lo que no cabe **se pierde** (no
pasa al renglón siguiente). Por eso la sentencia se pega **una línea por renglón** (baja de renglón con Tab o la
flecha):

```sql
CREATE TABLE SDSANTISH1.CLIENTES (
  ID INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  NOMBRE VARCHAR(30) NOT NULL,
  CIUDAD VARCHAR(20) NOT NULL,
  FECHA_ALTA TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
```

Pulsa Enter. Resultado esperado: `Table CLIENTES in SDSANTISH1 created but was not journaled.` Es un aviso, no un
error: la tabla funciona. Que no tenga journal es la razón por la que los pasos 6 y 7 compilan con `COMMIT(*NONE)`.

Si aparece `Token ... was not valid`, alguna línea se cortó al pegar: revisa en pantalla que cada línea esté completa.

**5.3 Ponerle descripción** (opcional). En STRSQL:

```sql
LABEL ON TABLE SDSANTISH1.CLIENTES IS 'Clientes del conector'
```

**5.4 Comprobar que existe.** En STRSQL:

```sql
SELECT * FROM SDSANTISH1.CLIENTES
```

Debe mostrar las columnas `ID`, `NOMBRE`, `CIUDAD`, `FECHA_ALTA` sin filas. Sal con `F3` (opción 1 para guardar la
sesión o 4 para salir sin guardar, da igual).

> Alternativa sin STRSQL: escribe `RUNSQL`, pulsa **F4**, pega la sentencia en el campo *SQL statement*, pon
> `*NONE` en *Commitment control* y `*SQL` en *Naming*, y pulsa Enter.

---

## Paso 6 — Programa CLIINS (guardar clientes)

Inserta un cliente en `CLIENTES` y devuelve el `ID` asignado. Lleva SQL dentro: el tipo es **`SQLRPGLE`** y se
compila con **`CRTSQLRPGI`**.

**6.1 Crear el miembro.** Comando AS400:

```
ADDPFM FILE(SDSANTISH1/QRPGLESRC) MBR(CLIINS) SRCTYPE(SQLRPGLE) TEXT('Alta de clientes')
```

**6.2 Abrir el miembro.** VS Code → **OBJECT BROWSER** → `SDSANTISH1` → `QRPGLESRC` → `CLIINS.SQLRPGLE`.

**6.3 Pegar** y guardar con `Ctrl+S` (`**free` en la columna 1):

```
**free
// ------------------------------------------------------------------
// CLIINS: registra un cliente en SDSANTISH1.CLIENTES y devuelve
// el ID que le asigna la tabla.
// ------------------------------------------------------------------

dcl-pi *n;
  pNombre char(30); // Entrada
  pCiudad char(20); // Entrada
  pId     int(10);  // Salida: ID generado
  pStatus char(10); // Salida: SUCCESS o ERROR
  pMsg    char(50); // Salida: detalle
end-pi;

// La libreria no tiene journal: se trabaja sin control de compromiso
exec sql set option commit = *none, naming = *sql;

pId = 0;

if %trim(pNombre) = '' or %trim(pCiudad) = '';
  pStatus = 'ERROR';
  pMsg = 'Nombre y ciudad son obligatorios';
  *inlr = *on;
  return;
endif;

// Inserta y recupera el ID generado en la misma sentencia
exec sql
  select ID into :pId
    from final table (
      insert into SDSANTISH1.CLIENTES (NOMBRE, CIUDAD)
        values (trim(:pNombre), upper(trim(:pCiudad))));

if sqlcode = 0;
  pStatus = 'SUCCESS';
  pMsg = 'Cliente registrado';
else;
  pStatus = 'ERROR';
  pMsg = 'Error SQL ' + %char(sqlcode);
endif;

*inlr = *on;
return;
```

**6.4 Compilar.** Comando AS400:

```
CRTSQLRPGI OBJ(SDSANTISH1/CLIINS) SRCFILE(SDSANTISH1/QRPGLESRC) COMMIT(*NONE)
```

Resultado esperado: `Program CLIINS created in library SDSANTISH1`.

**6.5 PCML.** Ya existe: `src/main/resources/pcml/cliins.pcml`.

```xml
<pcml version="4.0">
   <program name="cliins" path="/QSYS.LIB/SDSANTISH1.LIB/CLIINS.PGM">
      <data name="nombre" type="char" length="30" usage="input"/>
      <data name="ciudad" type="char" length="20" usage="input"/>

      <data name="id"     type="int"  length="4" precision="31" usage="output"/>
      <data name="status" type="char" length="10" usage="output"/>
      <data name="msg"    type="char" length="50" usage="output"/>
   </program>
</pcml>
```

**6.6 Probar** (ejecútalo dos o tres veces cambiando los datos, para tener registros en el paso 7):

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "cliins", "parametros": { "nombre": "CARLOS", "ciudad": "Lima" } }'
```

Respuesta esperada:

```json
{ "pcml": "cliins", "programa": "cliins", "resultado": { "id": 1, "status": "SUCCESS", "msg": "Cliente registrado" } }
```

**6.7 Verificar en la base de datos.** `STRSQL` y:

```sql
SELECT * FROM SDSANTISH1.CLIENTES ORDER BY ID
```

---

## Paso 7 — Programa CLICON (consultar clientes)

Devuelve los clientes de una ciudad (o todos si la ciudad va en blanco), máximo 50.

**7.1 Crear el miembro.** Comando AS400:

```
ADDPFM FILE(SDSANTISH1/QRPGLESRC) MBR(CLICON) SRCTYPE(SQLRPGLE) TEXT('Consulta de clientes')
```

**7.2 Abrir el miembro.** VS Code → **OBJECT BROWSER** → `SDSANTISH1` → `QRPGLESRC` → `CLICON.SQLRPGLE`.

**7.3 Pegar** y guardar con `Ctrl+S` (`**free` en la columna 1):

```
**free
// ------------------------------------------------------------------
// CLICON: consulta los clientes de SDSANTISH1.CLIENTES por ciudad.
// Ciudad en blanco = todos. Devuelve como maximo 50 registros.
// ------------------------------------------------------------------

dcl-c MAX_CLIENTES 50;

dcl-ds cliente_t qualified template;
  id        int(10);
  nombre    char(30);
  ciudad    char(20);
  fechaAlta char(19); // AAAA-MM-DD HH:MM:SS
end-ds;

dcl-pi *n;
  pCiudad   char(20);                            // Entrada
  pCantidad int(10);                             // Salida
  pClientes likeds(cliente_t) dim(MAX_CLIENTES); // Salida
  pStatus   char(10);                            // Salida
  pMsg      char(50);                            // Salida
end-pi;

dcl-s maxFilas int(10) inz(MAX_CLIENTES);

// La libreria no tiene journal: se trabaja sin control de compromiso
exec sql set option commit = *none, naming = *sql;

clear pClientes;
pCantidad = 0;

exec sql declare cClientes cursor for
  select ID, NOMBRE, CIUDAD,
         varchar_format(FECHA_ALTA, 'YYYY-MM-DD HH24:MI:SS')
    from SDSANTISH1.CLIENTES
   where :pCiudad = '' or CIUDAD = upper(trim(:pCiudad))
   order by ID;

exec sql open cClientes;
if sqlcode < 0;
  pStatus = 'ERROR';
  pMsg = 'Error SQL al abrir ' + %char(sqlcode);
  *inlr = *on;
  return;
endif;

// Lee hasta 50 filas de una vez directo al arreglo de salida
exec sql fetch next from cClientes for :maxFilas rows into :pClientes;

if sqlcode < 0;
  pStatus = 'ERROR';
  pMsg = 'Error SQL al leer ' + %char(sqlcode);
else;
  pCantidad = sqlerrd(3);
  pStatus = 'SUCCESS';
  pMsg = %char(pCantidad) + ' cliente(s) encontrado(s)';
endif;

exec sql close cClientes;

*inlr = *on;
return;
```

**7.4 Compilar.** Comando AS400:

```
CRTSQLRPGI OBJ(SDSANTISH1/CLICON) SRCFILE(SDSANTISH1/QRPGLESRC) COMMIT(*NONE)
```

**7.5 PCML.** Ya existe: `src/main/resources/pcml/clicon.pcml`.

```xml
<pcml version="4.0">
   <struct name="cliente">
      <data name="id"        type="int"  length="4" precision="31"/>
      <data name="nombre"    type="char" length="30"/>
      <data name="ciudad"    type="char" length="20"/>
      <data name="fechaAlta" type="char" length="19"/>
   </struct>

   <program name="clicon" path="/QSYS.LIB/SDSANTISH1.LIB/CLICON.PGM">
      <data name="ciudad"   type="char" length="20" usage="input"/>

      <data name="cantidad" type="int"  length="4" precision="31" usage="output"/>
      <data name="clientes" type="struct" struct="cliente" count="cantidad" outputsize="3650" usage="output"/>
      <data name="status"   type="char" length="10" usage="output"/>
      <data name="msg"      type="char" length="50" usage="output"/>
   </program>
</pcml>
```

**7.6 Probar** una ciudad:

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "clicon", "parametros": { "ciudad": "lima" } }'
```

Respuesta esperada:

```json
{
  "pcml": "clicon",
  "programa": "clicon",
  "resultado": {
    "cantidad": 1,
    "clientes": [ { "id": 1, "nombre": "CARLOS", "ciudad": "LIMA", "fechaAlta": "2026-10-23 17:10:00" } ],
    "status": "SUCCESS",
    "msg": "1 cliente(s) encontrado(s)"
  }
}
```

**7.7 Probar** todos los clientes: el mismo comando con `"ciudad": ""`.

> Si este paso responde con un error de jt400 sobre `cantidad` o `clientes`, en `clicon.pcml` cambia
> `count="cantidad"` por `count="50"`, reinicia el conector (tecla `s` en la consola de Quarkus) y prueba de nuevo.

---

## Paso 8 — Agregar cualquier otro programa

Misma receta que los pasos anteriores:

**8.1 Crear el miembro.** Comando AS400, eligiendo archivo y tipo según el lenguaje:

| Lenguaje | Comando |
|---|---|
| CL | `ADDPFM FILE(SDSANTISH1/QCLSRC) MBR(NOMBRE) SRCTYPE(CLLE)` |
| RPG | `ADDPFM FILE(SDSANTISH1/QRPGLESRC) MBR(NOMBRE) SRCTYPE(RPGLE)` |
| RPG con SQL | `ADDPFM FILE(SDSANTISH1/QRPGLESRC) MBR(NOMBRE) SRCTYPE(SQLRPGLE)` |

**8.2 Abrir, pegar el fuente y guardar** desde VS Code (OBJECT BROWSER). Guarda también una copia en
`src/main/as400/` del proyecto.

**8.3 Compilar.** Comando AS400:

| Lenguaje | Comando |
|---|---|
| CL | `CRTBNDCL PGM(SDSANTISH1/NOMBRE) SRCFILE(SDSANTISH1/QCLSRC)` |
| RPG | `CRTBNDRPG PGM(SDSANTISH1/NOMBRE) SRCFILE(SDSANTISH1/QRPGLESRC) PGMINFO(*PCML) INFOSTMF('/tmp/nombre.pcml')` |
| RPG con SQL | `CRTSQLRPGI OBJ(SDSANTISH1/NOMBRE) SRCFILE(SDSANTISH1/QRPGLESRC) COMMIT(*NONE)` |

**8.4 Crear el PCML** en `src/main/resources/pcml/nombre.pcml`:

- RPG: descarga el que generó el compilador (VS Code → IFS BROWSER → `/tmp/nombre.pcml` → Download).
- CL o SQLRPGLE: escríbelo a mano con la [tabla de tipos](#tipos-cl--rpg--pcml), con los parámetros en el mismo
  orden que el programa.
- Comprueba que el `path` sea `/QSYS.LIB/SDSANTISH1.LIB/NOMBRE.PGM`.

**8.5 Reiniciar el conector** para que vea el PCML nuevo: tecla `s` en la consola de Quarkus.

**8.6 Probar:**

```bash
curl --location 'http://localhost:8080/programa' \
--header 'Content-Type: application/json' \
--data '{ "pcml": "nombre", "parametros": { "...": "..." } }'
```

El valor de `"pcml"` es el nombre del archivo sin `.pcml`.

---

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| `release version ... not supported` al levantar el conector | El JDK es más antiguo de lo que pide el `pom.xml`. Instala JDK 21+ (`./mvnw -v` para ver cuál usa Maven). |
| Quarkus no arranca: falta `as400.password` | Ejecuta `export AS400_PASSWORD=...` en la misma terminal antes de `./mvnw quarkus:dev`. |
| Al compilar: `Severity 40 errors found` | Comando AS400 `WRKSPLF` → busca el archivo con el nombre del programa → opción `5` → busca los mensajes `RNF` o `SQL`. Lo más común: `**free` no está en la columna 1. |
| `SQL7008` al guardar en la tabla | El programa se compiló sin `COMMIT(*NONE)`. Vuelve a compilar con ese parámetro. |
| El fuente se ve "cortado" en SEU | SEU muestra desde la columna 6. Shift+F7 para ver desde la columna 1. Es solo visual. |
| Respuesta 400 `Falta el parámetro X` | El body no trae ese parámetro o el nombre no coincide con el PCML (mayúsculas no importan). |
| Respuesta 400 `No existe el PCML X` | El archivo no está en `src/main/resources/pcml/` o el conector no se reinició (tecla `s`). |
| Respuesta 502 con `CPF...` | El programa falló en el AS400. Comando AS400 `DSPMSGD CPFxxxx` para ver qué significa. |
| Respuesta 502 `Error al llamar al programa` | Contraseña incorrecta, o el programa del `path` no existe: comando AS400 `WRKOBJ OBJ(SDSANTISH1/*ALL) OBJTYPE(*PGM)`. |
| Respuesta 503 `Las N conexiones al AS400 están ocupadas` | Hay más llamadas simultáneas que conexiones en el pool. Sube `AS400_POOL_MAX_CONNECTIONS` o `AS400_POOL_ACQUIRE_TIMEOUT` (ver [Escalabilidad](#escalabilidad)). |
| Respuesta 503 `No se pudo conectar al AS400` | El AS400 no responde o el host es incorrecto. |

---

## Referencia

### Endpoint

`POST /programa`

```json
{
  "pcml": "nombre del .pcml sin extensión",
  "programa": "opcional, solo si el PCML define varios <program>",
  "parametros": { "nombre": "valor" }
}
```

- Estructuras → objetos JSON; arreglos (`count`) → listas.
- Los nombres no distinguen mayúsculas.
- `usage="input"`: obligatorio. `inputoutput`: opcional (si falta se rellena con blancos/ceros). `output`: no se
  envía.
- La respuesta trae en `resultado` los parámetros `output` e `inputoutput`.
- Errores, siempre como `{ "error": "..." }`: `400` la petición no encaja con el PCML · `502` el programa falló en
  el AS400 · `503` no hay conexión disponible con el AS400 · `500` el PCML está mal formado.

### Tipos CL / RPG / PCML

| CL | RPG | PCML |
|---|---|---|
| `TYPE(*CHAR) LEN(n)` | `char(n)` | `type="char" length="n"` |
| `TYPE(*DEC) LEN(p s)` | `packed(p:s)` | `type="packed" length="p" precision="s"` |
| — | `zoned(p:s)` | `type="zoned" length="p" precision="s"` |
| `TYPE(*INT) LEN(2)` | `int(5)` | `type="int" length="2" precision="15"` |
| `TYPE(*INT) LEN(4)` | `int(10)` | `type="int" length="4" precision="31"` |
| — | `int(20)` | `type="int" length="8" precision="63"` |
| `TYPE(*UINT) LEN(4)` | `uns(10)` | `type="int" length="4" precision="32"` |
| — | `dcl-ds` / `likeds(...)` | `<struct>` o `type="struct" struct="..."` |
| — | `dim(n)` | `count="n"` |

### Arquitectura (hexagonal)

El núcleo no conoce ni HTTP ni jt400: solo define modelos, reglas y contratos (puertos). Los adaptadores de
`infrastructure` implementan esos contratos, así que cambiar la forma de entrada (p. ej. una cola de mensajes) o de
salida (p. ej. XMLSERVICE en vez de jt400) no toca el núcleo.

```
src/main/java/com/as400/
├── config/
│   └── AS400Config.java                 Conexión y pool (application.properties / variables de entorno)
│
├── core/                                CAPA DE DOMINIO (sin dependencias externas)
│   ├── domain/
│   │   ├── ProgramDefinition.java       Programa y sus parámetros
│   │   ├── ParameterDefinition.java     Parámetro: dato simple o estructura, arreglo, usage
│   │   ├── ProgramRequest.java          Petición de ejecución
│   │   ├── ProgramResult.java           Salidas del programa
│   │   ├── ProgramInputValidator.java   Reglas: obligatorios, longitudes, rangos, relleno de arreglos
│   │   ├── DefinitionScope.java         Recorrido del árbol de parámetros y referencias de count
│   │   └── exception/                   Errores de negocio (petición inválida, AS400 caído...)
│   └── ports/
│       ├── inbound/
│       │   └── ExecuteProgramUseCase.java     Lo que ofrece el conector
│       └── outbound/
│           ├── ProgramDefinitionPort.java     De dónde salen las definiciones
│           └── As400ProgramPort.java          Cómo se llama a un programa del AS400
│
├── usecases/
│   └── ExecuteProgramService.java       Orquesta: definición → validación → llamada
│
└── infrastructure/                      CAPA DE ADAPTADORES
    ├── inbound/http/
    │   ├── ProgramController.java       POST /programa
    │   ├── ProgramRequestDto.java       Body (pcml, programa, parametros)
    │   ├── ProgramResponseDto.java      Respuesta (pcml, programa, resultado)
    │   └── HttpExceptionMapper.java     Errores del dominio → 400 / 500 / 502 / 503
    └── outbound/
        ├── pcml/
        │   ├── PcmlDefinitionRepository.java   Implementa ProgramDefinitionPort leyendo los .pcml
        │   └── PcmlResources.java              Ubicación de los .pcml en el classpath
        └── as400/
            ├── As400LegacyAdapter.java   Implementa As400ProgramPort con jt400
            ├── As400Connection.java      Pool de conexiones y límite de concurrencia
            └── mappers/
                └── PcmlDocumentMapper.java   Capa ACL: valores del dominio ↔ ProgramCallDocument de jt400

src/main/resources/pcml/          Un .pcml por programa
src/main/as400/                   Copia de los fuentes del AS400 (los mismos que se pegan arriba)
```

Flujo de una petición:

```
POST /programa ─▶ ProgramController ─▶ ExecuteProgramService
                                          ├─▶ ProgramDefinitionPort  (PcmlDefinitionRepository: lee el .pcml, en caché)
                                          ├─▶ ProgramInputValidator  (valida antes de ocupar una conexión)
                                          └─▶ As400ProgramPort       (As400LegacyAdapter: pool + jt400 + mapper)
```

### Escalabilidad

- **Pool de conexiones**: las conexiones al AS400 se reutilizan entre llamadas en vez de abrir una por petición.
- **Límite de concurrencia**: como mucho `max-connections` llamadas simultáneas; las demás esperan hasta
  `acquire-timeout` y, si no hay hueco, reciben `503` en vez de saturar el AS400.
- **Caché**: cada PCML se lee y se parsea una sola vez; cada llamada trabaja sobre una copia.
- **Validación previa**: una petición inválida se rechaza sin tocar el AS400.
- **Sin estado**: se pueden levantar varias instancias del conector detrás de un balanceador; el total de
  conexiones al AS400 será `instancias × max-connections`.

Configuración (en `application.properties` o como variables de entorno):

| Propiedad | Variable de entorno | Por defecto | Qué controla |
|---|---|---|---|
| `as400.pool.max-connections` | `AS400_POOL_MAX_CONNECTIONS` | `10` | Conexiones (y llamadas simultáneas) máximas |
| `as400.pool.acquire-timeout` | `AS400_POOL_ACQUIRE_TIMEOUT` | `30s` | Espera máxima por una conexión libre antes del `503` |
| `as400.pool.max-inactivity` | `AS400_POOL_MAX_INACTIVITY` | `5m` | Cierra conexiones sin uso |
| `as400.pool.max-lifetime` | `AS400_POOL_MAX_LIFETIME` | `1h` | Renueva conexiones antiguas |

### Tests

```bash
./mvnw test
```

Cubren la lectura de los PCML, las reglas de validación, la escritura en el documento de jt400 y los errores del
endpoint. No necesitan conexión al AS400.

### Limitaciones

- Tipos soportados en entradas: `char`, `packed`, `zoned`, `int`, `float`.
- Solo programas (`*PGM`), no procedimientos de programas de servicio.
