/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.web.taglib

import org.codehaus.groovy.grails.plugins.GrailsPluginManager
import org.codehaus.groovy.grails.web.pages.FastStringWriter

/**
 * Tags for developing javascript and ajax applications.
 *
 * @author Graeme Rocher
 */
class JavascriptTagLib  {

    /**
     * Mappings to the relevant files to be included for each library.
     */
    static final INCLUDED_LIBRARIES = "org.codehaus.grails.INCLUDED_JS_LIBRARIES"
    static final INCLUDED_JS = "org.codehaus.grails.INCLUDED_JS"
    static final CONTROLLER = "org.codehaus.groovy.grails.CONTROLLER"
    static final LIBRARY_MAPPINGS = [prototype: ['prototype/prototype']]

    static {
        LIBRARY_MAPPINGS.scriptaculous = LIBRARY_MAPPINGS.prototype + ['prototype/scriptaculous']
        LIBRARY_MAPPINGS.rico = LIBRARY_MAPPINGS.prototype + ['prototype/rico']
    }

    static final PROVIDER_MAPPINGS = [prototype: PrototypeProvider,
                                      scriptaculous: PrototypeProvider,
                                      rico: PrototypeProvider]

    GrailsPluginManager pluginManager

    /**
     * Includes a javascript src file, library or inline script
     * if the tag has no 'src' or 'library' attributes its assumed to be an inline script:<br/>
     *
     * &lt;g:javascript&gt;alert('hello')&lt;/g:javascript&gt;<br/>
     *
     * The 'library' attribute will attempt to use the library mappings defined above to import the
     * right js files and not duplicate imports eg.<br/>
     *
     * &lt;g:javascript library="scripaculous" /&gt; // imports all the necessary js for the scriptaculous library<br/>
     *
     * The 'src' attribute will merely import the js file but within the right context (ie inside the /js/ directory of
     * the Grails application:<br/>
     *
     * &lt;g:javascript src="myscript.js" /&gt; // actually imports '/app/js/myscript.js'
     *
     * @attr src The name of the javascript file to import. Will look in web-app/js dir
     * @attr library The name of the library to include. Either "prototype", "scriptaculous", "yahoo" or "dojo"
     * @attr plugin The plugin to look for the javascript in
     * @attr contextPath the context path to use (relative to the application context path). Defaults to "" or path to the plugin for a plugin view or template.
     * @attr base specifies the full base url to prepend to the library name
     */
    def javascript = { attrs, body ->

        setUpRequestAttributes()

        if (attrs.src) {
            javascriptInclude(attrs)
        }
        else if (attrs.library) {
            if (LIBRARY_MAPPINGS.containsKey(attrs.library)) {
                LIBRARY_MAPPINGS[attrs.library].each {
                    if (!request[INCLUDED_JS].contains(it)) {
                        request[INCLUDED_JS] << it
                        def newattrs = [:] + attrs
                        newattrs.src = it + '.js'
                        javascriptInclude(newattrs)
                    }
                }
                if (!request[INCLUDED_LIBRARIES].contains(attrs.library)) {
                    request[INCLUDED_LIBRARIES] << attrs.library
                }
            }
            else {
                if (!request[INCLUDED_LIBRARIES].contains(attrs.library)) {
                    def newattrs = [:] + attrs
                    newattrs.src = newattrs.remove('library') + '.js'
                    javascriptInclude(newattrs)
                    request[INCLUDED_LIBRARIES] << attrs.library
                    request[INCLUDED_JS] << attrs.library
                }
            }
        }
        else {
            out.println '<script type="text/javascript">'
            out.println body()
            out.println '</script>'
        }
    }

    private javascriptInclude(attrs) {
        def requestPluginContext
        if (attrs.plugin) {
            requestPluginContext = pluginManager.getPluginPath(attrs.remove('plugin')) ?: ''
        }
        else {
            if (attrs.contextPath != null) {
                requestPluginContext = attrs.remove('contextPath').toString()
            }
            else {
                requestPluginContext = pageScope.pluginContextPath ?: ''
            }
        }

        def writer = out
        writer << '<script type="text/javascript" src="'
        if (!attrs.base) {
            def baseUri = grailsAttributes.getApplicationUri(request)
            writer << baseUri << (baseUri.endsWith('/') ? '' : '/')
            if (requestPluginContext) {
                writer << (requestPluginContext.startsWith("/") ? requestPluginContext.substring(1) : requestPluginContext)
                writer << "/"
            }
            writer << 'js/'
        }
        else {
            writer << attrs.base
        }

        writer << attrs.src
        writer << '"'
        def otherAttrs = [:] + attrs
        otherAttrs.remove('base')
        otherAttrs.remove('src')
        otherAttrs.remove('library')
        otherAttrs.each {k, v -> writer << " $k=\"${v.encodeAsHTML()}\"" }
        writer.println '></script>'
    }

    /**
     * Creates a remote function call using the prototype library.
     *
     * @attr before The javascript function to call before the remote function call
     * @attr after The javascript function to call after the remote function call
     * @attr update Either a map containing the elements to update for 'success' or 'failure' states, or a string with the element to update in which cause failure events would be ignored
     * @attr action the name of the action to use in the link, if not specified the default action will be linked
     * @attr controller the name of the controller to use in the link, if not specified the current controller will be linked
     * @attr id The id to use in the link
     * @attr asynchronous Whether to do the call asynchronously or not (defaults to true, specified in 'options' array)
     * @attr method The method to use the execute the call (defaults to "post")
     */
    def remoteFunction = { attrs ->
        // before remote function
        def after = ''
        if (attrs.before) {
            out << "${attrs.remove('before')};"
        }
        if (attrs.after) {
            after = "${attrs.remove('after')};"
        }

        getProvider().doRemoteFunction(owner, attrs, out)
        attrs.remove('update')
        // after remote function
        if (after) {
            out << after
        }
    }

    private setUpRequestAttributes() {
        if (!request[INCLUDED_JS]) request[INCLUDED_JS] = []
        if (!request[INCLUDED_LIBRARIES]) request[INCLUDED_LIBRARIES] = []
    }

    /**
     * Normal map implementation does a shallow clone. This implements a deep clone for maps
     * using recursion.
     */
    private deepClone(Map map) {
        def cloned = [:]
        map.each { k,v ->
            if (v instanceof Map) {
                cloned[k] = deepClone(v)
            }
            else {
                cloned[k] = v
            }
        }
        return cloned
    }

    /**
     * A link to a remote uri that used the prototype library to invoke the link via ajax.
     *
     * @attr update Either a map containing the elements to update for 'success' or 'failure' states, or a string with the element to update in which cause failure events would be ignored
     * @attr before The javascript function to call before the remote function call
     * @attr after The javascript function to call after the remote function call
     * @attr asynchronous Whether to do the call asynchronously or not (defaults to true)
     * @attr method The method to use the execute the call (defaults to "post")
     * @attr controller The name of the controller to use in the link, if not specified the current controller will be linked
     * @attr action The name of the action to use in the link, if not specified the default action will be linked
     * @attr uri relative URI
     * @attr url A map containing the action,controller,id etc.
     * @attr base Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.
     * @attr absolute If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.
     * @attr id The id to use in the link
     * @attr fragment The link fragment (often called anchor tag) to use
     * @attr params A map containing URL query parameters
     * @attr mapping The named URL mapping to use to rewrite the link
     * @attr elementId the DOM element id
     */
    def remoteLink = { attrs, body ->
        out << '<a href="'

        def cloned = deepClone(attrs)
        out << createLink(cloned)

        out << '" onclick="'
        // create remote function
        out << remoteFunction(attrs)
        attrs.remove('url')
        out << 'return false;"'

        // handle elementId like link
        def elementId = attrs.remove('elementId')
        if (elementId) {
            out << " id=\"${elementId}\""
        }

        // process remaining attributes
        attrs.each { k,v ->
            out << ' ' << k << "=\"" << v << "\""
        }
        out << ">"
        // output the body
        out << body()

        // close tag
        out << "</a>"
    }

    /**
     * A field that sends its value to a remote link.
     * 
     * @attr name REQUIRED the name of the field
     * @attr value The initial value of the field
     * @attr paramName The name of the parameter send to the server
     * @attr action the name of the action to use in the link, if not specified the default action will be linked
     * @attr controller the name of the controller to use in the link, if not specified the current controller will be linked
     * @attr id The id to use in the link
     * @attr update Either a map containing the elements to update for 'success' or 'failure' states, or a string with the element to update in which cause failure events would be ignored
     * @attr before The javascript function to call before the remote function call
     * @attr after The javascript function to call after the remote function call
     * @attr asynchronous Whether to do the call asynchronously or not (defaults to true)
     * @attr method The method to use the execute the call (defaults to "post")
     */
    def remoteField = { attrs, body ->
        def paramName = attrs.paramName ? attrs.remove('paramName') : 'value'
        def value = attrs.remove('value')
        if (!value) value = ''

        out << "<input type=\"text\" name=\"${attrs.remove('name')}\" value=\"${value}\" onkeyup=\""

        if (attrs.params) {
            if (attrs.params instanceof Map) {
                attrs.params[paramName] = new JavascriptValue('this.value')
            }
            else {
                attrs.params += "+'${paramName}='+this.value"
            }
        }
        else {
            attrs.params = "'${paramName}='+this.value"
        }
        out << remoteFunction(attrs)
        attrs.remove('params')
        out << "\""
        attrs.remove('url')
        attrs.each { k,v->
            out << " $k=\"$v\""
        }
        out <<" />"
    }

    /**
     * A form which used prototype to serialize its parameters and submit via an asynchronous ajax call.
     * 
     * @attr name REQUIRED The form name
     * @attr url REQUIRED The url to submit to as either a map (containing values for the controller, action, id, and params) or a URL string
     * @attr action The action to execute as a fallback, defaults to the url if non specified
     * @attr update Either a map containing the elements to update for 'success' or 'failure' states, or a string with the element to update in which cause failure events would be ignored
     * @attr before The javascript function to call before the remote function call
     * @attr after The javascript function to call after the remote function call
     * @attr asynchronous Whether to do the call asynchronously or not (defaults to true)
     * @attr method The method to use the execute the call (defaults to "post")
     */
    def formRemote = { attrs, body ->
        if (!attrs.name) {
            throwTagError("Tag [formRemote] is missing required attribute [name]")
        }
        if (!attrs.url) {
            throwTagError("Tag [formRemote] is missing required attribute [url]")
        }

        // 'formRemote' does not support the 'params' attribute.
        if (attrs.params != null) {
            throwTagError("""\
Tag [formRemote] does not support the [params] attribute - add\
a 'params' key to the [url] attribute instead.""")
        }

        // get javascript provider
        def p = getProvider()
        def url = deepClone(attrs.url)

        // prepare form settings
        p.prepareAjaxForm(attrs)

        def params = [onsubmit:remoteFunction(attrs) + 'return false',
                      method: (attrs.method? attrs.method : 'POST' ),
                      action: (attrs.action? attrs.action : createLink(url))]
        attrs.remove('url')
        params.putAll(attrs)
        if (params.name && !params.id) {
            params.id = params.name
        }
        out << withTag(name:'form',attrs:params) {
            out << body()
        }
    }

    /**
     * Creates a form submit button that submits the current form to a remote ajax call.
     *
     * @attr url The url to submit to, either a map contraining keys for the action,controller and id or string value
     * @attr update Either a map containing the elements to update for 'success' or 'failure' states, or a string with the element to update in which cause failure events would be ignored
     * @attr before The javascript function to call before the remote function call
     * @attr after The javascript function to call after the remote function call
     * @attr asynchronous Whether to do the call asynchronously or not (defaults to true)
     * @attr method The method to use the execute the call (defaults to "post")
     */
    def submitToRemote = { attrs, body ->
        // get javascript provider
        def p = getProvider()
        // prepare form settings
        attrs.forSubmitTag = ".form"
        p.prepareAjaxForm(attrs)
        def params = [onclick: remoteFunction(attrs) + 'return false',
                      type: 'button',
                      name: attrs.remove('name'),
                      value: attrs.remove('value'),
                      id: attrs.remove('id'),
                      'class': attrs.remove('class')]

        out << withTag(name: 'input', attrs: params) {
            out << body()
        }
    }

    /**
     * Escapes a javascript string replacing single/double quotes and new lines.<br/>
     *
     * &lt;g:escapeJavascript&gt;This is some "text" to be escaped&lt;/g:escapeJavascript&gt;
     */
    def escapeJavascript = { attrs, body ->
        def js = ''
        if (body instanceof Closure) {
            def tmp = out
            def sw = new FastStringWriter()
            out = sw
            // invoke body
            out << body()
            // restore out
            out = tmp
            js = sw.toString()
        }
        else if (body instanceof String) {
            js = body
        }
        else if (attrs instanceof String) {
            js = attrs
        }
        out << js.replaceAll(/\r\n|\n|\r/, '\\\\n')
                 .replaceAll('"','\\\\"')
                 .replaceAll("'","\\\\'")
    }

    def setProvider = { attrs, body ->
        if (request[JavascriptTagLib.INCLUDED_LIBRARIES] == null) {
            request[JavascriptTagLib.INCLUDED_LIBRARIES] = []
        }
        request[JavascriptTagLib.INCLUDED_LIBRARIES] << attrs.library
    }

    /**
     * Returns the provider of the necessary function calls to perform Javascript functions
     */
    private JavascriptProvider getProvider() {
        setUpRequestAttributes()
        def providerClass = PROVIDER_MAPPINGS.find { request[JavascriptTagLib.INCLUDED_LIBRARIES]?.contains(it.key) }?.value
        if (providerClass == null) {
            providerClass = PrototypeProvider
        }
        return providerClass.newInstance()
    }
}

/**
 * Defines methods that a JavaScript provider should implement.
 *
 * @author Graeme Rocher
 */
interface JavascriptProvider {

    /**
     * Creates a remote function call
     *
     * @param The attributes to use
     * @param The output to write to
     */
    def doRemoteFunction(taglib,attrs, out)

    def prepareAjaxForm(attrs)
}

class JavascriptValue {
    def value

    JavascriptValue(value) {
        this.value = value
    }

    String toString() { "'+$value+'" }
}

/**
 * Prototype implementation of JavaScript provider
 *
 * @author Graeme Rocher
 */
class PrototypeProvider implements JavascriptProvider {

    def doRemoteFunction(taglib,attrs, out) {
        out << 'new Ajax.'
        if (attrs.update) {
            out << 'Updater('
            if (attrs.update instanceof Map) {
                out << "{"
                def update = []
                if (attrs.update?.success) {
                    update << "success:'${attrs['update']['success']}'"
                }
                if (attrs.update?.failure) {
                    update << "failure:'${attrs['update']['failure']}'"
                }
                out << update.join(',')
                out << "},"
            }
            else {
                out << "'" << attrs.update << "',"
            }
            attrs.remove("update")
        }
        else {
            out << "Request("
        }
        out << "'"

        def url
        def jsParams = [:]

        if (attrs.params instanceof Map) {
            jsParams = attrs.params?.findAll { it.value instanceof JavascriptValue }
            jsParams?.each { attrs.params?.remove(it.key) }
        }

        if (attrs.url) {
            url = taglib.createLink(attrs.url)?.toString()
        }
        else {
            url = taglib.createLink(attrs)?.toString()
        }

        if (!attrs.params) attrs.params = [:]
        jsParams?.each { attrs.params[it.key] = it.value }

        def i = url?.indexOf('?')

        if (i > -1) {
            if (attrs.params instanceof String) {
                attrs.params += "+'&${url[i+1..-1].encodeAsJavaScript()}'"
            }
            else if (attrs.params instanceof Map) {
                def params = createQueryString(attrs.params)
                attrs.params = "'${params}${params ? '&' : ''}${url[i+1..-1].encodeAsJavaScript()}'"
            }
            else {
                attrs.params = "'${url[i+1..-1].encodeAsJavaScript()}'"
            }
            out << url[0..i-1]
        }
        else {
            out << url
        }
        out << "',"
        // process options
        out << getAjaxOptions(attrs)
        // close
        out << ');'
        attrs.remove('params')
    }

    private String createQueryString(params) {
        def allParams = []
        for (entry in params) {
            def value = entry.value
            def key = entry.key
            if (value instanceof JavascriptValue) {
                allParams << "${key.encodeAsURL()}='+${value.value}+'"
            }
            else {
                allParams << "${key.encodeAsURL()}=${value.encodeAsURL()}".encodeAsJavaScript()
            }
        }
        if (allParams.size() == 1) {
            return allParams[0]
        }

        return allParams.join('&')
    }

    // helper function to build ajax options
    def getAjaxOptions(options) {
        def ajaxOptions = []

        // necessary defaults
        def optionsAttr = options.remove('options')
        def async = optionsAttr?.remove('asynchronous')
        if (async != null) {
            ajaxOptions << "asynchronous:${async}"
        }
        else {
            ajaxOptions << "asynchronous:true"
        }

        def eval = optionsAttr?.remove('evalScripts')
        if (eval != null) {
            ajaxOptions << "evalScripts:${eval}"
        }
        else {
            ajaxOptions << "evalScripts:true"
        }

        if (options) {
            // process callbacks
            def callbacks = options.findAll { k,v ->
                k ==~ /on(\p{Upper}|\d){1}\w+/
            }
            callbacks.each { k,v ->
                ajaxOptions << "${k}:function(e){${v}}"
                options.remove(k)
            }
            if (options.params) {
                def params = options.remove('params')
                if (params instanceof Map) {
                    params = createQueryString(params)
                }
                ajaxOptions << "parameters:${params}"
            }
        }
        // remaining options
        optionsAttr.each { k, v ->
            if (k != 'url') {
                switch(v) {
                    case 'true': ajaxOptions << "${k}:${v}"; break
                    case 'false': ajaxOptions << "${k}:${v}"; break
                    case ~/\s*function(\w*)\s*/: ajaxOptions << "${k}:${v}"; break
                    case ~/Insertion\..*/: ajaxOptions << "${k}:${v}"; break
                    default:ajaxOptions << "${k}:'${v}'"; break
                }
            }
        }

        return "{${ajaxOptions.join(',')}}"
    }

    def prepareAjaxForm(attrs) {
        if (!attrs.forSubmitTag) attrs.forSubmitTag = ""

        attrs.params = "Form.serialize(this${attrs.remove('forSubmitTag')})".toString()
    }
}
