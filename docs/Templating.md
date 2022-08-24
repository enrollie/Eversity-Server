# Report generating in Eversity

## Preamble

Eversity has a report generating engine that allows users to generate various reports based on data in the system.
Those reports are generated in various formats (DOCX, CSV, md, etc.) and can be downloaded by users.
Internally, Eversity uses [XDocReport](https://github.com/opensagres/xdocreport) library to generate docx reports
and [FastCSV](https://github.com/osiegmar/FastCSV) library to generate CSV reports.
Unfortunately, in order to keep Eversity Plugin API footprint as small as possible, it was decided not to open those
libraries to plugins module. However, plugin developers are free to use those libraries as compile-only dependencies,
since Eversity server has them in a classpath.

## Report creation from a user standpoint

Server has a REST endpoint that allows users to get a list of available reports and suggested field values for each
report.

## Report creation from a plugin developer standpoint

Again, plugin developers are encouraged to use libraries mentioned earlier as compile-only dependencies, since Eversity
server has them in a classpath. They have been proven to be fast and well-suited for those purposes.

However, plugin developers are also not prohibited from using any other library to generate any other template.

### Register your template

Call `ApplicationProvider.templatingEngine.registerTemplate` method with your template metadata and call handler.
This handler will be called if user wants to generate a report with your template.

**Important!** Eversity Server does not guarantee that valid data will be supplied to your template. Please, throw an
exception if you encounter an error.

### Generate a report

When handler mentioned earlier is called, it must generate a report in a temporary file and return it. Plugin must not
do anything with generated file afterwards: it will be handled by server properly.
This file may be in any format you
like (though, consider user experience opening that file: not every user is comfortable reading i.e. YAML 😉).

